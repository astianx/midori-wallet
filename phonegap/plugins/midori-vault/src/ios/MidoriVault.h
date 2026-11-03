#import <Cordova/CDVPlugin.h>
@interface MidoriVault : CDVPlugin <UIDocumentPickerDelegate>
@property(nonatomic, strong) CDVInvokedUrlCommand* fileCommand;
@property(nonatomic, assign) BOOL exporting;
@property(nonatomic, strong) NSURL* temporaryFile;
- (void)saveFile:(CDVInvokedUrlCommand*)command;
- (void)readFile:(CDVInvokedUrlCommand*)command;
- (void)seal:(CDVInvokedUrlCommand*)command;
- (void)open:(CDVInvokedUrlCommand*)command;
- (void)remove:(CDVInvokedUrlCommand*)command;
@end
