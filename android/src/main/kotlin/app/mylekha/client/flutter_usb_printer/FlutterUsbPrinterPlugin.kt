package app.mylekha.client.flutter_usb_printer

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.annotation.NonNull
import app.mylekha.client.flutter_usb_printer.adapter.USBPrinterAdapter
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.*

/** FlutterUsbPrinterPlugin */
class FlutterUsbPrinterPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  private var adapter: USBPrinterAdapter? = null
  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var activity: Activity
  private lateinit var context: Context

  private fun getAdapter(result: Result): USBPrinterAdapter? =
      adapter ?: run {
          result.error("NOT_INITIALIZED", "USB adapter not initialized", null)
          null
      }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_usb_printer")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.getApplicationContext()
    adapter = USBPrinterAdapter.getInstance()
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
        "getUSBDeviceList" -> {
          getUSBDeviceList(result)
        }
        "connect" -> {
          val vendorId = call.argument<Int>("vendorId")
          val productId = call.argument<Int>("productId")
          connect(vendorId!!, productId!!, result)
        }
        "close" -> {
          close(result)
        }
        "isConnected" -> {
          isConnected(result)
        }
        "setChunkDelay" -> {
          val ms = call.argument<Int>("ms")
          setChunkDelay(ms, result)
        }
        "printText" -> {
          val text = call.argument<String>("text")
          printText(text, result)
        }
        "printRawText" -> {
          val raw = call.argument<String>("raw")
          printRawText(raw, result)
        }
        "write" -> {
          val data = call.argument<ByteArray>("data")
          write(data, result)
        }
        else -> {
          result.notImplemented()
        }
    }
  }

  private fun getUSBDeviceList(result: Result) {
    val a = getAdapter(result) ?: return
    val usbDevices = a.getDeviceList()
    val list = ArrayList<HashMap<String, String?>>()
    for (usbDevice in usbDevices) {
      val deviceMap: HashMap<String, String?> = HashMap()
      deviceMap["deviceName"] = usbDevice.deviceName
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        deviceMap["manufacturer"] = usbDevice.manufacturerName
      }else{
        deviceMap["manufacturer"] = "unknown";
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        deviceMap["productName"] = usbDevice.productName
      }else{
        deviceMap["productName"] = "unknown";
      }
      deviceMap["deviceId"] = Integer.toString(usbDevice.deviceId)
      deviceMap["vendorId"] = Integer.toString(usbDevice.vendorId)
      deviceMap["productId"] = Integer.toString(usbDevice.productId)
      list.add(deviceMap)
    }
    result.success(list)
  }

  private fun connect(vendorId: Int, productId: Int, result: Result) {
    val a = getAdapter(result) ?: return
    a.selectDevice(vendorId, productId) { granted ->
      result.success(granted)
    }
  }

  private fun close(result: Result) {
    val a = getAdapter(result) ?: return
    a.closeConnectionIfExists()
    result.success(true)
  }

  private fun isConnected(result: Result) {
    val a = getAdapter(result) ?: return
    result.success(a.isConnected())
  }

  private fun setChunkDelay(ms: Int?, result: Result) {
    val a = getAdapter(result) ?: return
    a.chunkDelayMs = (ms ?: 0).toLong()
    result.success(true)
  }

  private fun printText(text: String?, result: Result) {
    val a = getAdapter(result) ?: return
    scope.launch {
      try {
        val success = withContext(Dispatchers.IO) { a.printText(text!!) }
        result.success(success)
      } catch (e: Exception) {
        result.error("PRINT_TEXT_ERROR", e.message, null)
      }
    }
  }

  private fun printRawText(base64Data: String?, result: Result) {
    val a = getAdapter(result) ?: return
    scope.launch {
      try {
        val success = withContext(Dispatchers.IO) { a.printRawText(base64Data!!) }
        result.success(success)
      } catch (e: Exception) {
        result.error("PRINT_RAW_TEXT_ERROR", e.message, null)
      }
    }
  }

  private fun write(bytes: ByteArray?, result: Result) {
    val a = getAdapter(result) ?: return
    scope.launch {
      try {
        val success = withContext(Dispatchers.IO) { a.write(bytes!!) }
        result.success(success)
      } catch (e: Exception) {
        result.error("WRITE_ERROR", e.message, null)
      }
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    scope.cancel()
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    print("onAttachedToActivity")
    activity = binding.activity
    adapter?.init(activity)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    // This call will be followed by onReattachedToActivityForConfigChanges().
    print("onDetachedFromActivityForConfigChanges");
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    print("onAttachedToActivity")
    onAttachedToActivity(binding)
  }

  override fun onDetachedFromActivity() {
    // This call will be followed by onDetachedFromActivity().
    print("onDetachedFromActivity")
  }
}