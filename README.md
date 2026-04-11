# Questions?

***Please Please Please email john@umbriac.com with any questions!!***

# How to upload a new version of the app to the phone

### From an .apk file

1. Plug the Android phone into a computer
2. Pull down the notification shade, and select the notification "charge this device via USB" and "tap for more options"
3. Select "file transfer"
4. Copy the .apk file into the "Download" folder in the phone
5. Eject the phone and disconnect the Android phone from the computer
6. Open the "files" app on the Android phone
7. Open the Downloads folder
8. Click the three dots next to the APK file and click "Install"
9. Click "Install" or "Update" on the prompt
10. Click "More options" then "Install without scanning" (it's ok to scan, just takes longer)

*Google will be breaking this install method soon (see https://www.androidauthority.com/google-android-sideloading-unverified-apps-new-rules-3650343/)*



# Important things to know when trying to read this mess

## Important files

- app/src/main/java/com/example/a7_0project/MainActivity.java
  - This is the main file that most everything will go in.
  - The code here handles reading from the bluetooth connection, updating the screen, a much more
- app/src/main/AndroidManifest.xml
  - This is how you set system 