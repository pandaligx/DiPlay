package com.shilapi.xcertplay

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings

internal object DiPlayBluetooth {
    /** System-stack observations only: no radio changes, addresses, names or bonded-device data. */
    // The permission probe below gates protected reads; each read also handles permission revocation.
    @SuppressLint("MissingPermission")
    fun diagnosticReport(context: Context): String = buildString {
        appendLine("Android system Bluetooth diagnostics (read-only)")
        appendLine("featureBluetooth=${diagnosticValue { context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) }}")
        appendLine("featureBluetoothLe=${diagnosticValue { context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }}")

        val permission = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        val permissionGranted = diagnosticRead {
            context.checkPermission(permission, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED
        }
        appendLine("connectionPermission=$permission")
        appendLine("connectionPermissionStatus=${permissionGranted.failure ?: if (permissionGranted.value == true) "granted" else "denied"}")

        val manager = diagnosticRead {
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        }
        appendLine("bluetoothManager=${manager.failure ?: if (manager.value == null) "absent" else "available"}")
        val adapter = manager.value?.let { service -> diagnosticRead { service.adapter } }
        appendLine("adapter=${adapter?.failure ?: when {
            manager.value == null -> "not_checked_no_system_manager"
            adapter?.value == null -> "absent"
            else -> "available"
        }}")

        val current = adapter?.value
        val unavailable = when {
            current == null -> "not_checked_no_system_adapter"
            permissionGranted.failure != null -> "not_checked_permission_unknown"
            permissionGranted.value != true -> "not_checked_permission_denied"
            else -> null
        }
        if (unavailable != null) {
            appendLine("adapterEnabled=$unavailable")
            appendLine("adapterState=$unavailable")
        } else if (current != null) {
            appendLine("adapterEnabled=${diagnosticValue { current.isEnabled }}")
            appendLine("adapterState=${diagnosticValue {
                when (val state = current.state) {
                    BluetoothAdapter.STATE_OFF -> "OFF"
                    BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
                    BluetoothAdapter.STATE_ON -> "ON"
                    BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
                    else -> "UNKNOWN($state)"
                }
            }}")
        }
        append("A missing Android system Bluetooth stack does not establish that the head unit's vendor Bluetooth is unavailable; these observations do not identify the cause of a wireless retry.")
    }

    private data class DiagnosticRead<T>(val value: T? = null, val failure: String? = null)

    private inline fun <T> diagnosticRead(read: () -> T): DiagnosticRead<T> = try {
        DiagnosticRead(value = read())
    } catch (_: SecurityException) {
        DiagnosticRead(failure = "unavailable(SecurityException)")
    } catch (_: RuntimeException) {
        DiagnosticRead(failure = "unavailable(RuntimeException)")
    } catch (_: LinkageError) {
        DiagnosticRead(failure = "unavailable(LinkageError)")
    }

    private inline fun <T> diagnosticValue(read: () -> T): String =
        diagnosticRead(read).let { it.failure ?: it.value.toString() }

    // API19 needs only BLUETOOTH; on 23+ the optional privileged read is gated below.
    // Never request LOCAL_MAC_ADDRESS, and tolerate permission revocation at the read.
    @SuppressLint("MissingPermission")
    fun localAddress(context: Context): String? {
        val adapter = try {
            if (Build.VERSION.SDK_INT < 23 ||
                context.checkSelfPermission("android.permission.LOCAL_MAC_ADDRESS") == PackageManager.PERMISSION_GRANTED
            ) (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.address else null
        } catch (_: SecurityException) {
            // Newer Android limits local MAC access to privileged apps. Try the existing
            // firmware-provided setting below without requesting a privileged permission.
            null
        } catch (_: RuntimeException) {
            null
        } catch (_: LinkageError) {
            null
        }
        val setting = runCatching { Settings.Secure.getString(context.contentResolver, "bluetooth_address") }.getOrNull()
        return listOfNotNull(adapter, setting).firstOrNull {
            Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}").matches(it) &&
                !it.startsWith("02:00:00:00:00:") && it != "00:00:00:00:00:00"
        }
    }
}
