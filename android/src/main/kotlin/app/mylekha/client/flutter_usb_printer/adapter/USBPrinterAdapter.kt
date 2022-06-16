package app.mylekha.client.flutter_usb_printer.adapter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import android.util.Base64
import android.util.Log
import android.widget.Toast
import java.nio.charset.Charset
import java.util.*


class USBPrinterAdapter {

    private var mInstance: USBPrinterAdapter? = null


    private val LOG_TAG = "Flutter USB Printer"
    private var mContext: Context? = null
    private var mUSBManager: UsbManager? = null
    private var mPermissionIntent: PendingIntent? = null
    private var mUsbDevice: UsbDevice? = null
    private var mUsbDeviceConnection: UsbDeviceConnection? = null
    private var mUsbInterface: UsbInterface? = null
    private var mEndPoint: UsbEndpoint? = null

    private val ACTION_USB_PERMISSION = "app.mylekha.client.flutter_usb_printer.USB_PERMISSION"




    fun getInstance(): USBPrinterAdapter? {
        if (mInstance == null) {
            mInstance = this;
        }
        return mInstance
    }

    private val mUsbDeviceReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            var action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized(this) {
                    val usbDevice =
                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.i(
                            LOG_TAG,
                            "Success to grant permission for device " + usbDevice!!.deviceId + ", vendor_id: " + usbDevice.vendorId + " product_id: " + usbDevice.productId
                        )
                        mUsbDevice = usbDevice
                    } else {
                        Toast.makeText(
                            context,
                            "User refused to give USB device permissions" + usbDevice!!.deviceName,
                            Toast.LENGTH_LONG
                        ).show()
                    }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mPermissionIntent =
                PendingIntent.getBroadcast(mContext, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        } else {
            mPermissionIntent =
                PendingIntent.getBroadcast(mContext, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        mContext!!.registerReceiver(mUsbDeviceReceiver, filter)
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

    fun selectDevice(vendorId: Int, productId: Int): Boolean {
        if (mUsbDevice == null || mUsbDevice!!.vendorId != vendorId || mUsbDevice!!.productId != productId) {
            closeConnectionIfExists()
            val usbDevices = getDeviceList()
            for (usbDevice in usbDevices) {
                if (usbDevice.vendorId == vendorId && usbDevice.productId == productId) {
                    Log.v(
                        LOG_TAG,
                        "Request for device: vendor_id: " + usbDevice.vendorId + ", product_id: " + usbDevice.productId
                    )
                    closeConnectionIfExists()
                    mUSBManager!!.requestPermission(usbDevice, mPermissionIntent)
                    return true
                }
            }
            return false
        }
        return true
    }

    private fun openConnection(): Boolean {
        if (mUsbDevice == null) {
            Log.e(LOG_TAG, "USB Device is not initialized")
            return false
        }
        if (mUSBManager == null) {
            Log.e(LOG_TAG, "USB Manager is not initialized")
            return false
        }
        if (mUsbDeviceConnection != null) {
            Log.i(LOG_TAG, "USB Connection already connected")
            return true
        }
        val usbInterface = mUsbDevice!!.getInterface(0)
        for (i in 0 until usbInterface.endpointCount) {
            val ep = usbInterface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_OUT) {
                    val usbDeviceConnection = mUSBManager!!.openDevice(mUsbDevice)
                    if (usbDeviceConnection == null) {
                        Log.e(LOG_TAG, "failed to open USB Connection")
                        return false
                    }
                    Toast.makeText(mContext, "Device connected", Toast.LENGTH_SHORT).show()
                    return if (usbDeviceConnection.claimInterface(usbInterface, true)) {
                        mEndPoint = ep
                        mUsbInterface = usbInterface
                        mUsbDeviceConnection = usbDeviceConnection
                        true
                    } else {
                        usbDeviceConnection.close()
                        Log.e(LOG_TAG, "failed to claim usb connection")
                        false
                    }
                }
            }
        }
        return true
    }

    fun printText(text: String): Boolean {
        Log.v(LOG_TAG, "start to print text")
        val isConnected = openConnection()
        return if (isConnected) {
            Log.v(LOG_TAG, "Connected to device")
            Thread {
                val bytes = text.toByteArray(Charset.forName("UTF-8"))
                val b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                Log.i(LOG_TAG, "Return Status: b-->$b")
            }.start()
            true
        } else {
            Log.v(LOG_TAG, "failed to connected to device")
            false
        }
    }

    fun printRawText(data: String): Boolean {
        Log.v(LOG_TAG, "start to print raw data $data")
        val isConnected = openConnection()
        return if (isConnected) {
            Log.v(LOG_TAG, "Connected to device")
            Thread {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                val b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                Log.i(LOG_TAG, "Return Status: $b")
            }.start()
            true
        } else {
            Log.v(LOG_TAG, "failed to connected to device")
            false
        }
    }

    fun write(bytes: ByteArray): Boolean {
        Log.v(LOG_TAG, "start to print raw data $bytes")
        val isConnected = openConnection()
        return if (isConnected) {
            Log.v(LOG_TAG, "Connected to device")
            Thread {
                val b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                Log.i(LOG_TAG, "Return Status: $b")
            }.start()
            true
        } else {
            Log.v(LOG_TAG, "failed to connected to device")
            false
        }
    }
}