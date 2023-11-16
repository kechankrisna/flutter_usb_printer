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
    companion object {
        private const val TAG = "Flutter USB Printer"

        private const val ACTION_USB_PERMISSION =
            "app.mylekha.client.flutter_usb_printer.USB_PERMISSION"
    }

    private var mContext: Context? = null
    private var mUSBManager: UsbManager? = null
    private var mUsbDevice: UsbDevice? = null
    private var mUsbDeviceConnection: UsbDeviceConnection? = null
    private var mUsbInterface: UsbInterface? = null
    private var mEndPoint: UsbEndpoint? = null


    private val mUsbDeviceReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    var usbDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

                    if (usbDevice == null) {
                        Log.d(TAG, "onReceive: Received null USB")

                        val vendorId = intent.getIntExtra("vendorId", -1)
                        val productId = intent.getIntExtra("productId", -1)

                        if (vendorId == -1 || productId == -1) {
                            Log.d(TAG, "onReceive: No vendor id or product id")
                            return
                        }

                        usbDevice = getDeviceByVendorAndProductId(vendorId, productId) ?: return
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.i(
                            TAG,
                            "Success to grant permission for device " + usbDevice.deviceId + ", vendor_id: " + usbDevice.vendorId + " product_id: " + usbDevice.productId
                        )
                        mUsbDevice = usbDevice
                    } else {
                        Toast.makeText(
                            context,
                            "User refused to give USB device permissions" + usbDevice.deviceName,
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
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        mContext!!.registerReceiver(mUsbDeviceReceiver, filter)
        Log.v(TAG, "USB Printer initialized")
    }


    fun closeConnectionIfExists() {
        if (mUsbDeviceConnection != null) {
            mUsbDeviceConnection!!.releaseInterface(mUsbInterface)
            mUsbDeviceConnection!!.close()
            mUsbInterface = null
            mEndPoint = null
            mUsbDeviceConnection = null
            mUsbDevice = null
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

    private fun getDeviceByVendorAndProductId(vendorId: Int, productId: Int): UsbDevice? {
        val usbDevices = getDeviceList()
        for (usbDevice in usbDevices) {
            if (usbDevice.vendorId == vendorId && usbDevice.productId == productId)
                return usbDevice
        }
        return null
    }

    fun selectDevice(vendorId: Int, productId: Int): Boolean {
        if (mUsbDevice == null || mUsbDevice!!.vendorId != vendorId || mUsbDevice!!.productId != productId) {
            closeConnectionIfExists()
            val usbDevice = getDeviceByVendorAndProductId(vendorId, productId) ?: return false

            Log.v(TAG, "Request for device: vendor_id: " + usbDevice.vendorId + ", product_id: " + usbDevice.productId)
            closeConnectionIfExists()

            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT

            val intent = Intent(ACTION_USB_PERMISSION).apply {
                putExtra("vendorId", vendorId)
                putExtra("productId", productId)
            }

            val mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, intent, flag)

            mUSBManager!!.requestPermission(usbDevice, mPermissionIntent)
            return true
        }
        return true
    }

    private fun openConnection(): Boolean {
        if (mUsbDevice == null) {
            Log.e(TAG, "USB Device is not initialized")
            return false
        }
        if (mUSBManager == null) {
            Log.e(TAG, "USB Manager is not initialized")
            return false
        }
        if (mUsbDeviceConnection != null) {
            Log.i(TAG, "USB Connection already connected")
            return true
        }
        val usbInterface = mUsbDevice!!.getInterface(0)
        for (i in 0 until usbInterface.endpointCount) {
            val ep = usbInterface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_OUT) {
                    val usbDeviceConnection = mUSBManager!!.openDevice(mUsbDevice)
                    if (usbDeviceConnection == null) {
                        Log.e(TAG, "failed to open USB Connection")
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
                        Log.e(TAG, "failed to claim usb connection")
                        false
                    }
                }
            }
        }
        return false
    }

    fun printText(text: String): Boolean {
        Log.v(TAG, "start to print text")
        val isConnected = openConnection()
        return if (isConnected) {
            Log.v(TAG, "Connected to device")
            Thread {
                val bytes = text.toByteArray(Charset.forName("UTF-8"))
                val b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                Log.i(TAG, "Return Status: b-->$b")
            }.start()
            true
        } else {
            Log.v(TAG, "failed to connected to device")
            false
        }
    }

    fun printRawText(data: String): Boolean {
        Log.v(TAG, "start to print raw data $data")
        val isConnected = openConnection()
        return if (isConnected) {
            Log.v(TAG, "Connected to device")
            Thread {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                val b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                Log.i(TAG, "Return Status: $b")
            }.start()
            true
        } else {
            Log.v(TAG, "failed to connected to device")
            false
        }
    }

    fun write(bytes: ByteArray): Boolean {
        Log.v(TAG, "start to print raw data $bytes")
        val isConnected = openConnection()
        return if (isConnected) {
            Log.v(TAG, "Connected to device")
            Thread {
                val b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                Log.i(TAG, "Return Status: $b")
            }.start()
            true
        } else {
            Log.v(TAG, "failed to connected to device")
            false
        }
    }
}
