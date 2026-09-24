# Google Play listing draft

Status: proposed copy only; no Play Store listing or release has been published.

## App name

Baby Buddy Pocket

## Short description

An independent Android companion. Requires an existing Baby Buddy server.

## Full description

Baby Buddy Pocket is an independent Android companion for Baby Buddy. You need access to an existing Baby Buddy server to use it with your family’s records. Server hosting and server setup are not included.

Need a server? Start with the Baby Buddy Railway template: https://railway.com/deploy/baby-buddy-self-hosted-baby-tracker--baby-buddy

Connect using your server’s HTTPS address and API token, or scan the QR code from Baby Buddy’s Add a device page. Your server administrator can provide access if someone else hosts it for your family.

Keep your baby’s day in one place:

- Log feeds, sleep, diaper changes, tummy time, pumping, and other activities supported by your server.
- See recent activity, a shared timeline, and simple trends.
- Start shared timers, see elapsed time in an ongoing notification, and save completed activities.
- Choose which activity panels you see and remember frequently used choices.
- Use light or dark mode.

After your first sync, read downloaded records and log activities offline. Starts and stops are saved on your phone. Open the app online to sync pending work with your server. Other caregivers see changes after their apps sync; sharing is not available while disconnected. Uncertain uploads are clearly marked for review.

Your records sync directly with the Baby Buddy server you choose. No advertising, analytics, or additional developer cloud service is included. The app is open source and can be built yourself.

Want to explore before setting up a server? Try the offline demo with fictional sample data. Demo entries reset when the app process restarts and are not uploaded.

Edit or delete activities in the app, including saving deletion requests offline. Manage children and photos in the Baby Buddy server web interface. Available fields and actions depend on your server version and account permissions.

Baby Buddy Pocket is an independent project, not an official release of the Baby Buddy server project or the Baby Buddy Companion iPhone app.

## Suggested first screenshot

Headline: **Requires your Baby Buddy server**

Caption: Connect your existing server, or explore the offline demo.

Show the real welcome screen with the server requirement visible. This requirement should be visible before users install, not only after they open the app.

## Publication notes

The delivered APK remains a debug-signed sideload/testing build. This draft is not a Play Store submission. See [release preparation](PLAY-RELEASE.md) for bundle signing, privacy/app-content declarations, and testing before submission. Preserve `com.babybuddypocket.app` for future updates.
