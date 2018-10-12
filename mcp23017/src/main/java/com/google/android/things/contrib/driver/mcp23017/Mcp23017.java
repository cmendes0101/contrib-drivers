/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.things.contrib.driver.mcp23017;

import android.support.annotation.VisibleForTesting;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

/**
 * Driver for the Microchip Mcp23017
 * e.g. https://www.microchip.com/wwwproducts/en/MCP23017
 */
@SuppressWarnings("WeakerAccess")
public class Mcp23017 implements AutoCloseable {
    private static final String TAG = "Mcp23017";

    public static final int MAX_PINS = 16;

    /**
     * Default I2C slave address for the MCP230xx family.
     */
    public static final int I2C_ADDRESS = 0x20;

    /**
     * Mcp23017 register map
     */
    // Input or Output modes
    private static final int IODIRA_REGISTER = 0x00;
    private static final int IODIRB_REGISTER = 0x01;

    // Write or read value
    private static final int GPIOA_REGISTER = 0x12;
    private static final int GPIOB_REGISTER = 0x13;

    // Pull-up resister
    private static final int GPPUA_REGISTER = 0x0C;
    private static final int GPPUB_REGISTER = 0x0D;

    // Output
    private static final int OLATA_REGISTER = 0x14;
    private static final int OLATB_REGISTER = 0x15;

    private static final boolean INPUT = true;
    private static final boolean OUTPUT = false;

    private I2cDevice mDevice;

    private int mOutput = 0x00;
    private int mDirection = 0x00;

    private int mPullups = 0x00;

    /**
     * Create a new Mcp23017 controller.
     *
     * @throws IOException
     */
    public Mcp23017()
            throws IOException {
        try {
            PeripheralManagerService manager = new PeripheralManagerService();
            I2cDevice device = manager.openI2cDevice(BoardDefaults.getI2CPort(), I2C_ADDRESS);
            init(device);
        } catch (IOException|RuntimeException e) {
            try {
                close();
            } catch (IOException|RuntimeException ignored) {
            }
            throw e;
        }
    }

    /**
     * Constructor invoked from unit tests.
     */
    @VisibleForTesting
    /*package*/ Mcp23017(I2cDevice i2cDevice) throws IOException {
        init(i2cDevice);
    }

    /**
     * Initialize peripheral defaults from the constructor.
     */
    private void init(I2cDevice i2cDevice)
            throws IOException {
        if (i2cDevice == null) {
            throw new IllegalArgumentException("Must provide I2C device");
        }

        mDevice = i2cDevice;

        setAllPinModes(OUTPUT);
    }

    public boolean setPinMode(int pin, boolean direction) {
        if (direction == INPUT) {
            mDirection |= getBitValue(pin);
        } else {
            mDirection &= ~getBitValue(pin);
        }

        try {
            if (pin <= 8) {
                mDevice.writeRegByte(OLATA_REGISTER, (byte) (mDirection & 0xFF));
                return true;
            }

            mDevice.writeRegByte(OLATB_REGISTER, (byte) (mDirection >> 8));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public int readPin(int pin) throws IOException {
        if (pin < 0 || pin > MAX_PINS) throw
                new AssertionError("Pin number " + pin + " is not valid. Must be between 0 and " + (MAX_PINS - 1));

        if (pin <= 8) {
            int value = mDevice.readRegByte(GPIOA_REGISTER);
            return (value >> pin) & 1;
        } else {
            int value = mDevice.readRegByte(GPIOB_REGISTER);
            return (value >> (pin - 8)) & 1;
        }
    }

    public int setPullup(int pin, int value) {
        if (pin < 0 || pin > MAX_PINS) throw
                new AssertionError("Pin number " + pin + " is not valid. Must be between 0 and " + (MAX_PINS - 1));

        if (value == 1) {
            mPullups |= getBitValue(pin);
        } else {
            mPullups &= ~getBitValue(pin);
        }

        try {
            if (pin <= 8) {
                mDevice.writeRegByte(GPPUA_REGISTER, (byte) (mPullups & 0xFF));
            }

            mDevice.writeRegByte(GPPUB_REGISTER, (byte) (mPullups >> 8));
            return mPullups;
        } catch (IOException e) {
            e.printStackTrace();
            return mPullups;
        }
    }

    public int setOutput(int pin, int value) {
        if (pin < 0 || pin > MAX_PINS) throw
                new AssertionError("Pin number " + pin + " is not valid. Must be between 0 and " + (MAX_PINS - 1));

        int newValue;
        if (value == 1) {
            mOutput = newValue = mOutput | getBitValue(pin);
        } else {
            mOutput = newValue = mOutput & ~getBitValue(pin);
        }

        try {
            if (pin <= 8) {
                mDevice.writeRegByte(OLATA_REGISTER, (byte) (newValue & 0xFF));
                return newValue;
            }

            mDevice.writeRegByte(OLATB_REGISTER, (byte) (newValue >> 8));
            return newValue;
        } catch (IOException e) {
            e.printStackTrace();
            return newValue;
        }
    }

    @Override
    public void close() throws IOException {

        if (mDevice != null) {
            try {
                mDevice.close();
            } finally {
                mDevice = null;
            }
        }
    }

    /**
     * Write bitmask of input channels that should be enabled.
     * @param direction true to enable all inputs, false to disable them.
     * @throws IOException
     */
    public void setAllPinModes(boolean direction) throws IOException {
        if (direction) {
            mDirection = 0xFFFF;
            mDevice.writeRegWord(OLATA_REGISTER, (byte) mOutput);
        } else {
            mDirection = 0x0000;
            mDevice.writeRegWord(OLATA_REGISTER, (byte) mOutput);
        }

        mDevice.writeRegWord(GPPUA_REGISTER, (byte) mPullups);

        mOutput = mDevice.readRegWord(OLATA_REGISTER);
    }

    public static int getBitValue(int pin) {
        return (1 << pin);
    }

}
