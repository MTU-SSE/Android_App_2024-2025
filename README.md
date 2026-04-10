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

# How to get the .CSV file from the HUD Display
1. The driver will use the lap button during a race to create a race recording (automatically happens when 4 laps are completed)
2. Connect the HUD phone and a computer to the same wifi network
3. In the "more data" section of the app there will be a URL similar to http://141.219.12.235:8080/ type that into a browser on the computer connected to that same wifi network (or hotspot) as the phone. The URL needs start with http:// (not https!!) and end with :8080 for this to work
4. Click on the file with the correct timestamp to download the .CSV record for that run. The file name has the date and time. If the file you are looking for is not there, click the "refresh" button at the top of that page
5. Once the .CSV file is downloaded, go to https://sse.enterprise.mtu.edu/data.html and upload the .csv file to that website. It will then give you a bunch of info on that run! (that website was almost entirely AI generated, so lmk if there are things that look wrong)

### common problems
- **No URL?:** Ensure that the HUD phone is connected to a wifi network, then close and re-open the app. The URL should appear within 10 seconds.

Let John know if you have any questions!
