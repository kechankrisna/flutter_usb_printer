package app.mylekha.client.flutter_usb_printer.adapter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Base64
import android.util.Log
import android.widget.Toast
import java.nio.charset.Charset


class USBPrinterAdapter {

    companion object {
        @Volatile
        private var mInstance: USBPrinterAdapter? = null

        fun getInstance(): USBPrinterAdapter =
            mInstance ?: synchronized(this) {
                mInstance ?: USBPrinterAdapter().also { mInstance = it }
            }
    }


    private val LOG_TAG = "Flutter USB Printer"
    private var mContext: Context? = null
    private var mUSBManager: UsbManager? = null
    private var mPermissionIndent: PendingIntent? = null
    private var mUsbDevice: UsbDevice? = null
    private var mUsbDeviceConnection: UsbDeviceConnection? = null
    private var mUsbInterface: UsbInterface? = null
    private var mEndPoint: UsbEndpoint? = null

    private val ACTION_USB_PERMISSION = "app.mylekha.client.flutter_usb_printer.USB_PERMISSION"

    private var mPermissionCallback: ((Boolean) -> Unit)? = null

    private val mUsbDeviceReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val usbDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && usbDevice != null) {
                        Log.i(
                            LOG_TAG,
                            "Permission granted for device " + usbDevice.deviceId + ", vendor_id: " + usbDevice.vendorId + " product_id: " + usbDevice.productId
                        )
                        mUsbDevice = usbDevice
                    } else if (!granted) {
                        Log.w(LOG_TAG, "Permission denied for device: ${usbDevice?.deviceName ?: "unknown"}")
                    }
                    val cb = mPermissionCallback
                    mPermissionCallback = null
                    cb?.invoke(granted)
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                if (mUsbDevice != null) {
                    Toast.makeText(context, "USB device has been turned off", Toast.LENGTH_LONG)
                        .show()
                    closeConnectionIfExists()
                }
            }
        }
    }

    fun init(reactContext: Context?) {
        mContext = reactContext
        mUSBManager = mContext!!.getSystemService(Context.USB_SERVICE) as UsbManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            mPermissionIndent = PendingIntent.getBroadcast(
                mContext!!,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            mPermissionIndent = PendingIntent.getBroadcast(
                mContext!!,
                0,
                Intent(ACTION_USB_PERMISSION),
                0
            )
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mContext!!.registerReceiver(mUsbDeviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            mContext!!.registerReceiver(mUsbDeviceReceiver, filter)
        }
        Log.v(LOG_TAG, "USB Printer initialized")
    }


    fun closeConnectionIfExists() {
        if (mUsbDeviceConnection != null) {
            mUsbDeviceConnection!!.releaseInterface(mUsbInterface)
            mUsbDeviceConnection!!.close()
            mUsbInterface = null
            mEndPoint = null
            mUsbDeviceConnection = null
        }
    }

    fun getDeviceList(): List<UsbDevice> {
        if (mUSBManager == null) {
            Toast.makeText(
                mContext,
                "USB Manager is not initialized while get device list",
                Toast.LENGTH_LONG
            ).show()
            return emptyList()
        }
        return ArrayList(mUSBManager!!.deviceList.values)
    }

    fun selectDevice(vendorId: Int, productId: Int, onResult: (Boolean) -> Unit) {
        if (mUsbDevice != null && mUsbDevice!!.vendorId == vendorId && mUsbDevice!!.productId == productId) {
            onResult(true)
            return
        }
        closeConnectionIfExists()
        val usbDevice = getDeviceList().firstOrNull { it.vendorId == vendorId && it.productId == productId }
        if (usbDevice == null) {
            onResult(false)
            return
        }
        closeConnectionIfExists()
        Log.v(LOG_TAG, "Request for device: vendor_id: ${usbDevice.vendorId}, product_id: ${usbDevice.productId}")
        if (mUSBManager!!.hasPermission(usbDevice)) {
            mUsbDevice = usbDevice
            onResult(true)
            return
        }
        mPermissionCallback = onResult
        mUSBManager!!.requestPermission(usbDevice, mPermissionIndent)
    }

    private fun openConnection(): Boolean {
        if (mUsbDevice == null) {
            Log.e(LOG_TAG, "USB device is not initialized")
            return false
        }
        if (mUSBManager == null) {
            Log.e(LOG_TAG, "USB Manager is not initialized")
            return false
        }
        if (mUsbDeviceConnection != null) {
            Log.i(LOG_TAG, "USB Connection already open")
            return true
        }
        for (ifaceIndex in 0 until mUsbDevice!!.interfaceCount) {
            val usbInterface = mUsbDevice!!.getInterface(ifaceIndex)
            for (i in 0 until usbInterface.endpointCount) {
                val ep = usbInterface.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                    val usbDeviceConnection = mUSBManager!!.openDevice(mUsbDevice)
                    if (usbDeviceConnection == null) {
                        Log.e(LOG_TAG, "Failed to open USB device — permission may be missing")
                        return false
                    }
                    return if (usbDeviceConnection.claimInterface(usbInterface, true)) {
                        mEndPoint = ep
                        mUsbInterface = usbInterface
                        mUsbDeviceConnection = usbDeviceConnection
                        Log.i(LOG_TAG, "USB connection opened on interface $ifaceIndex endpoint ${ep.endpointNumber}")
                        true
                    } else {
                        usbDeviceConnection.close()
                        Log.e(LOG_TAG, "Failed to claim USB interface $ifaceIndex")
                        false
                    }
                }
            }
        }
        Log.e(LOG_TAG, "No BULK OUT endpoint found on device ${mUsbDevice!!.deviceName}")
        return false
    }

    fun printText(text: String): Boolean {
        Log.v(LOG_TAG, "start to print text")
        if (!openConnection()) {
            Log.e(LOG_TAG, "failed to open connection for printText")
            return false
        }
        val bytes = text.toByteArray(Charset.forName("UTF-8"))
        val b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
        Log.i(LOG_TAG, "printText transfer status: $b")
        return b >= 0
    }

    fun printRawText(data: String): Boolean {
        Log.v(LOG_TAG, "start to print raw text")
        if (!openConnection()) {
            Log.e(LOG_TAG, "failed to open connection for printRawText")
            return false
        }
        val bytes = Base64.decode(data, Base64.DEFAULT)
        val b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
        Log.i(LOG_TAG, "printRawText transfer status: $b")
        return b >= 0
    }

    fun write(bytes: ByteArray): Boolean {
        Log.v(LOG_TAG, "start to write ${bytes.size} bytes")
        if (!openConnection()) {
            Log.e(LOG_TAG, "failed to open connection for write")
            return false
        }
        val b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
        Log.i(LOG_TAG, "write transfer status: $b")
        return b >= 0
    }
}