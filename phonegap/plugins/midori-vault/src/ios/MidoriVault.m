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
- (void)documentPickerWasCancelled:(UIDocumentPickerViewController*)controller { [self reply:self.fileCommand value:nil status:errSecUserCanceled]; [self finishPicker]; }
- (void)finishPicker {
  if (self.temporaryFile) [[NSFileManager defaultManager] removeItemAtURL:self.temporaryFile.URLByDeletingLastPathComponent error:nil];
  self.temporaryFile = nil; self.fileCommand = nil;
}
@end
