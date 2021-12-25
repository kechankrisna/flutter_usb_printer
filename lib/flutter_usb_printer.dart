import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/services.dart';

class FlutterUsbPrinter {
  static const MethodChannel _channel =
      const MethodChannel('flutter_usb_printer');

  /// [getUSBDeviceList]
  /// get list of available usb device on android
  static Future<List<Map<String, dynamic>>> getUSBDeviceList() async {
    if (Platform.isAndroid) {
      List<dynamic> devices = await _channel.invokeMethod('getUSBDeviceList');
      print(devices);
      var result = devices
          .cast<Map<dynamic, dynamic>>()
          .map((e) => Map<String, dynamic>.from(e))
          .toList();
      return result;
    } else {
      return <Map<String, dynamic>>[];
    }
  }

  /// [connect]
  /// connect to a printer vai vendorId and productId
  Future<bool?> connect(int vendorId, int productId) async {
    Map<String, dynamic> params = {
      "vendorId": vendorId,
      "productId": productId
    };
    final bool? result = await _channel.invokeMethod('connect', params);
    return result;
  }

  /// [close]
  /// close the connection after print with usb printer
  Future<bool?> close() async {
    final bool? result = await _channel.invokeMethod('close');
    return result;
  }

  /// [printText]
  /// print text
  Future<bool?> printText(String text) async {
    Map<String, dynamic> params = {"text": text};
    final bool? result = await _channel.invokeMethod('printText', params);
    return result;
  }

  /// [printRawText]
  /// print raw text
  Future<bool?> printRawText(String text) async {
    Map<String, dynamic> params = {"raw": text};
    final bool? result = await _channel.invokeMethod('printRawText', params);
    return result;
  }

  /// [write]
  /// write data byte
  Future<bool?> write(Uint8List data) async {
    Map<String, dynamic> params = {"data": data};
    final bool? result = await _channel.invokeMethod('write', params);
    return result;
  }
}
