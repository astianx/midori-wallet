#import "MidoriVault.h"
#import <Security/Security.h>
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
@implementation MidoriVault
- (NSMutableDictionary*)query:(NSString*)identifier {
  return [@{(__bridge id)kSecClass:(__bridge id)kSecClassGenericPassword, (__bridge id)kSecAttrService:@"org.astian.midori.vault.v1", (__bridge id)kSecAttrAccount:identifier} mutableCopy];
}
- (void)reply:(CDVInvokedUrlCommand*)command value:(NSString*)value status:(OSStatus)status {
  CDVPluginResult* result = status == errSecSuccess ? [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:value] : [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Protected mobile storage is unavailable or invalid"];
  [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}
- (void)seal:(CDVInvokedUrlCommand*)command {
  NSString* value = [command argumentAtIndex:0 withDefault:nil andClass:[NSString class]];
  if (!value || value.length > 65536) { [self reply:command value:nil status:errSecParam]; return; }
  NSString* identifier = [NSUUID UUID].UUIDString;
  NSMutableDictionary* query = [self query:identifier];
  query[(__bridge id)kSecValueData] = [value dataUsingEncoding:NSUTF8StringEncoding];
  query[(__bridge id)kSecAttrAccessible] = (__bridge id)kSecAttrAccessibleWhenUnlockedThisDeviceOnly;
  query[(__bridge id)kSecAttrSynchronizable] = @NO;
  [self reply:command value:identifier status:SecItemAdd((__bridge CFDictionaryRef)query, NULL)];
}
- (void)open:(CDVInvokedUrlCommand*)command {
  NSString* identifier = [command argumentAtIndex:0 withDefault:nil andClass:[NSString class]];
  if (!identifier || identifier.length != 36) { [self reply:command value:nil status:errSecParam]; return; }
  NSMutableDictionary* query = [self query:identifier]; query[(__bridge id)kSecReturnData] = @YES;
  CFTypeRef result = NULL; OSStatus status = SecItemCopyMatching((__bridge CFDictionaryRef)query, &result);
  NSData* data = CFBridgingRelease(result);
  [self reply:command value:data ? [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] : nil status:status];
}
- (void)remove:(CDVInvokedUrlCommand*)command {
  NSString* identifier = [command argumentAtIndex:0 withDefault:nil andClass:[NSString class]];
  if (!identifier || identifier.length != 36) { [self reply:command value:nil status:errSecParam]; return; }
  OSStatus status = SecItemDelete((__bridge CFDictionaryRef)[self query:identifier]);
  [self reply:command value:nil status:status == errSecItemNotFound ? errSecSuccess : status];
}
- (void)receipt:(CDVInvokedUrlCommand*)command share:(BOOL)share {
  if (self.fileCommand) { [self reply:command value:nil status:errSecParam]; return; }
  NSString* name = [command argumentAtIndex:0 withDefault:nil andClass:[NSString class]];
  NSString* mime = [command argumentAtIndex:1 withDefault:nil andClass:[NSString class]];
  NSString* encoded = [command argumentAtIndex:2 withDefault:nil andClass:[NSString class]];
  BOOL pdf = [mime isEqualToString:@"application/pdf"];
  NSRegularExpression* pattern = [NSRegularExpression regularExpressionWithPattern:@"^midori-receipt-[0-9a-f-]{36}\\.(png|pdf)$" options:0 error:nil];
  if (!name || !encoded || encoded.length > 12*1024*1024 || (![mime isEqualToString:@"image/png"] && !pdf) || ![pattern firstMatchInString:name options:0 range:NSMakeRange(0,name.length)]) { [self reply:command value:nil status:errSecParam]; return; }
  NSData* data = [[NSData alloc] initWithBase64EncodedString:encoded options:0];
  const unsigned char png[] = {137,80,78,71,13,10,26,10};
  if (!data || data.length < 8 || data.length > 8*1024*1024 || (pdf && (![name hasSuffix:@".pdf"] || memcmp(data.bytes,"%PDF-",5))) || (!pdf && (![name hasSuffix:@".png"] || memcmp(data.bytes,png,8)))) { [self reply:command value:nil status:errSecParam]; return; }
  NSURL* folder = [NSURL fileURLWithPath:[NSTemporaryDirectory() stringByAppendingPathComponent:[NSUUID UUID].UUIDString] isDirectory:YES];
  [[NSFileManager defaultManager] createDirectoryAtURL:folder withIntermediateDirectories:YES attributes:nil error:nil];
  self.temporaryFile = [folder URLByAppendingPathComponent:name];
  if (![data writeToURL:self.temporaryFile atomically:YES]) { [self reply:command value:nil status:errSecParam]; return; }
  self.fileCommand = command; self.exporting = YES;
  if (share) {
    UIActivityViewController* controller = [[UIActivityViewController alloc] initWithActivityItems:@[self.temporaryFile] applicationActivities:nil];
    controller.popoverPresentationController.sourceView = self.viewController.view;
    controller.popoverPresentationController.sourceRect = CGRectMake(CGRectGetMidX(self.viewController.view.bounds),CGRectGetMidY(self.viewController.view.bounds),1,1);
    controller.completionWithItemsHandler = ^(UIActivityType activity, BOOL completed, NSArray* items, NSError* error) {
      CDVPluginResult* result = error ? [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Receipt could not be shared"] : [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"cancelled":@(!completed)}];
      [self.commandDelegate sendPluginResult:result callbackId:command.callbackId]; [self finishPicker];
    };
    [self.viewController presentViewController:controller animated:YES completion:nil];
  } else { UIDocumentPickerViewController* picker = [[UIDocumentPickerViewController alloc] initForExportingURLs:@[self.temporaryFile] asCopy:YES]; picker.delegate = self; [self.viewController presentViewController:picker animated:YES completion:nil]; }
}
- (void)shareReceipt:(CDVInvokedUrlCommand*)command { [self receipt:command share:YES]; }
- (void)saveReceipt:(CDVInvokedUrlCommand*)command { [self receipt:command share:NO]; }

- (void)saveFile:(CDVInvokedUrlCommand*)command {
  if (self.fileCommand) { [self reply:command value:nil status:errSecParam]; return; }
  NSString* text = [command argumentAtIndex:0 withDefault:nil andClass:[NSString class]];
  if (!text || text.length > 32768) { [self reply:command value:nil status:errSecParam]; return; }
  NSURL* folder = [NSURL fileURLWithPath:[NSTemporaryDirectory() stringByAppendingPathComponent:[NSUUID UUID].UUIDString] isDirectory:YES];
  [[NSFileManager defaultManager] createDirectoryAtURL:folder withIntermediateDirectories:YES attributes:nil error:nil];
  self.temporaryFile = [folder URLByAppendingPathComponent:@"midori-recovery.json"];
  if (![text writeToURL:self.temporaryFile atomically:YES encoding:NSUTF8StringEncoding error:nil]) { [self reply:command value:nil status:errSecParam]; return; }
  self.fileCommand = command; self.exporting = YES;
  UIDocumentPickerViewController* picker = [[UIDocumentPickerViewController alloc] initForExportingURLs:@[self.temporaryFile] asCopy:YES];
  picker.delegate = self; [self.viewController presentViewController:picker animated:YES completion:nil];
}
- (void)readFile:(CDVInvokedUrlCommand*)command {
  if (self.fileCommand) { [self reply:command value:nil status:errSecParam]; return; }
  self.fileCommand = command; self.exporting = NO;
  UIDocumentPickerViewController* picker = [[UIDocumentPickerViewController alloc] initForOpeningContentTypes:@[UTTypeJSON] asCopy:YES];
  picker.delegate = self; [self.viewController presentViewController:picker animated:YES completion:nil];
}
- (void)documentPicker:(UIDocumentPickerViewController*)controller didPickDocumentsAtURLs:(NSArray<NSURL*>*)urls {
  NSString* value = @"saved"; OSStatus status = errSecSuccess;
  if (!self.exporting) {
    NSURL* url = urls.firstObject; BOOL accessed = [url startAccessingSecurityScopedResource];
    NSNumber* size = nil; [url getResourceValue:&size forKey:NSURLFileSizeKey error:nil];
    if (size && size.unsignedLongLongValue <= 32768) value = [NSString stringWithContentsOfURL:url encoding:NSUTF8StringEncoding error:nil]; else value = nil;
    if (accessed) [url stopAccessingSecurityScopedResource]; if (!value) status = errSecParam;
  }
  [self reply:self.fileCommand value:value status:status]; [self finishPicker];
}
- (void)documentPickerWasCancelled:(UIDocumentPickerViewController*)controller {
  if ([self.temporaryFile.pathExtension isEqualToString:@"pdf"] || [self.temporaryFile.pathExtension isEqualToString:@"png"]) {
    CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"cancelled":@YES}];
    [self.commandDelegate sendPluginResult:result callbackId:self.fileCommand.callbackId];
  } else [self reply:self.fileCommand value:nil status:errSecUserCanceled];
  [self finishPicker];
}
- (void)finishPicker {
  if (self.temporaryFile) [[NSFileManager defaultManager] removeItemAtURL:self.temporaryFile.URLByDeletingLastPathComponent error:nil];
  self.temporaryFile = nil; self.fileCommand = nil;
}
@end
