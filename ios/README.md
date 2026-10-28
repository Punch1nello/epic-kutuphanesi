# Epic Kütüphanesi for iPhone

This is a native SwiftUI companion app for iOS 16 and newer. It includes the Epic Store in a WKWebView, library categories and sorting, best-effort collections, Turkish/English/German/Spanish/Russian UI strings, and Keychain-backed Epic sessions.

## Build and install on iPhone

1. On a Mac, copy `EpicLibraryIOS/Secrets.xcconfig.example` to `EpicLibraryIOS/Secrets.xcconfig` and add the Epic OAuth client secret to the local copy. `Secrets.xcconfig` is ignored by Git; never commit it or share it.
2. Install Xcode and open `EpicLibraryIOS.xcodeproj`.
3. Select the `EpicLibraryIOS` app target, then open **Signing & Capabilities** and select your Apple development team.
4. Connect an iPhone or choose an iOS simulator, then press **Run**.
5. On first launch, use **Epic girişini aç**, sign in on Epic's page, copy the authorization code back into the app, and connect the account. The app stores the returned session in iOS Keychain and refreshes it on later launches when Epic allows it.

Xcode and Apple's signing system are required to run a native iOS app on a real iPhone. The Windows workspace cannot compile or sign an iOS binary, so this project has not been built or device-tested here.

## Notes

- Epic's store WebView login has its own browser cookies; it is separate from the library API session.
- Epic collections rely on an undocumented launcher GraphQL surface and may return no collections if Epic changes it.
- The iOS app does not install APKs or self-update from GitHub. iOS updates must be distributed through Xcode, TestFlight, or an App Store listing.
- Epic may not provide playtime for every library entry. Missing values are marked in the UI. Tools are classified from explicit item metadata or the Unreal Engine namespace, rather than from words in game titles.
