package com.shilapi.xcertplay.transport

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.os.Build
import androidx.annotation.RequiresApi

internal fun selectUsbConfiguration(
    connection: UsbDeviceConnection,
    configuration: CarPlayUsbConfiguration,
): Boolean {
    if (Build.VERSION.SDK_INT >= 21) return UsbConfigurationApi21.select(connection, configuration)
    // GET_CONFIGURATION avoids resetting a configuration that another pipe already uses.
    require(configuration.id in 1..255) { "USB configuration was not resolved from descriptors" }
    val active = ByteArray(1)
    val read = connection.controlTransfer(UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_STANDARD,
        8, 0, 0, active, 1, CONTROL_TIMEOUT_MILLIS)
    if (read == 1 && (active[0].toInt() and 0xff) == configuration.id) return true
    return connection.controlTransfer(UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_STANDARD,
        9, configuration.id, 0, null, 0, CONTROL_TIMEOUT_MILLIS) == 0
}

internal fun selectUsbInterface(
    connection: UsbDeviceConnection,
    usbInterface: UsbInterface,
    alternateSetting: Int,
): Boolean {
    if (Build.VERSION.SDK_INT >= 21) return UsbConfigurationApi21.selectInterface(connection, usbInterface)
    require(alternateSetting in 0..255) { "USB alternate setting was not resolved from descriptors" }
    return connection.controlTransfer(
        UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_STANDARD or USB_RECIP_INTERFACE,
        11, alternateSetting, usbInterface.id, null, 0, CONTROL_TIMEOUT_MILLIS) == 0
}

/** Keeps references to the API21-only UsbConfiguration class out of API19 object signatures. */
@RequiresApi(21)
internal object UsbConfigurationApi21 {
    fun configurations(device: UsbDevice): List<CarPlayUsbConfiguration> =
        (0 until device.configurationCount).map { index ->
            val value = device.getConfiguration(index)
            val interfaces = (0 until value.interfaceCount).map(value::getInterface)
            CarPlayUsbConfiguration(value.id, interfaces,
                interfaces.associateWith { it.alternateSetting }, value)
        }

    fun alternateSetting(usbInterface: UsbInterface): Int = usbInterface.alternateSetting

    fun select(connection: UsbDeviceConnection, configuration: CarPlayUsbConfiguration): Boolean =
        connection.setConfiguration(configuration.platformConfiguration as UsbConfiguration)

    fun selectInterface(connection: UsbDeviceConnection, usbInterface: UsbInterface): Boolean =
        connection.setInterface(usbInterface)
}

private const val CONTROL_TIMEOUT_MILLIS = 1_000
private const val USB_RECIP_INTERFACE = 1
