# Google Play listing

Draft text and answers for the Play Console. Everything here was written from what the app does
today (see `Android.md` and the About screen); re-read it before submitting and change anything
that is no longer true. Play Console wording and requirements change, so check each form against
the live console.

## Before you submit

- [ ] Bump `versionName` (and `versionCode`) in `app/build.gradle.kts` when cutting the release
      (now `1.0` / `1`; every upload needs a higher `versionCode`).
- [ ] Build the signed release (`RELEASING.md`). Play wants an **app bundle** for new apps:
      `./gradlew bundleRelease` (the APK from `assembleRelease` is for sharing directly).
- [ ] Back up `anivision-release.jks` and its passwords. Consider Play App Signing, so the key you
      upload with can be reset if it is lost.
- [ ] Run the checklist in `RELEASING.md` on a second phone.
- [ ] Host the privacy policy below at a public URL (a GitHub Pages page or a repo file URL works)
      and paste that URL into the console.
- [ ] Take screenshots: Dog view, Cat view, Split, the blur and night boost controls, a gallery
      photo with the divider. Phone screenshots only; the app has no tablet layout work.

## Store listing

**App name** (30 characters max): `AniVision`

**Short description** (80 characters max, this one is 78):

> See the world as a dog or cat might: their colour vision, blur and night view.

**Full description** (4000 characters max):

> AniVision shows you an approximation of how a dog or a cat might see the world.
>
> Point your camera and the live view changes to match the colour vision of a dog or a cat. Both
> have two kinds of colour-sensitive cone cells instead of our three, so reds and greens fade
> towards muted yellows and browns, while blues and yellows stay clear.
>
> • Human, Dog and Cat views. Pick any two for a side-by-side comparison.
> • Split screen. Drag the divider to compare views on the same scene.
> • Blur. Optionally soften the image to hint at the lower sharpness of dog and cat eyesight.
> • Night boost. Optionally lift the shadows, since dogs and cats see better than us in dim light.
> • Take photos. Saved to Pictures/AniVision on your phone, and shareable from the app.
> • Import a photo from your gallery and view it the same way.
>
> The cat view is very similar to the dog view in colour, so it adds a few artistic extras: a
> built-in shadow lift, a softer edge to the picture, and a wider field of view where your phone
> has an ultra-wide camera.
>
> AniVision is an approximation based on published research on animal cone cells. It cannot show
> what a dog or cat actually experiences, and some numbers (especially the cat's) are approximate.
> Treat it as an illustration, not a measurement.
>
> AniVision has no ads, no account and no tracking. It uses the camera only to show the live view
> and take photos, and it never sends anything anywhere.

**Category:** Photography (alternative: Entertainment). **Tags:** Photography, Education.

**Contact email:** the developer email you want shown publicly.

## Data safety form

The app has no `INTERNET` permission, no analytics, no ads and no sign-in, so the answers are
simple. Confirm each against the manifest and dependencies at release time.

| Question | Answer |
|---|---|
| Does the app collect or share any of the required user data types? | No |
| Is all of the user data encrypted in transit? | Not applicable: no data leaves the device |
| Can users request that data is deleted? | Not applicable: no data is collected |
| Permissions declared | `CAMERA` only (live view and photos) |

Notes for the reviewer: photos are written to `Pictures/AniVision` through MediaStore on the
device and never uploaded. Sharing goes through the Android share sheet and only when the user taps
Share. Gallery import uses the system picker. App backup is turned off (`allowBackup="false"`), so
the remembered view pair is not copied to the cloud either.

## Other console forms

- **Content rating:** answer the questionnaire honestly. The app has no user-generated content, no
  violence, no purchases and no ads, so it should rate as suitable for everyone. The camera sees
  whatever the user points it at, which the questionnaire may ask about.
- **Target audience:** adults and general audiences. If you pick children, extra Play family
  policies apply, so avoid it unless you intend that.
- **Ads:** no. **App access:** all functionality is available without a login.
- **Government, financial, health apps:** none of these apply.
- **Store listing assets:** 512×512 icon (export from the launcher icon), a 1024×500 feature
  graphic, and at least two phone screenshots.

## Privacy policy

Host this text at a public URL and use that URL in the console and, if you like, in the About
screen. Replace the bracketed parts.

> **Privacy policy for AniVision**
>
> Last updated: [date]
>
> AniVision ("the app") is made by [your name]. This policy explains what the app does with your
> information.
>
> **Information we collect.** None. The app has no account, no analytics, no advertising and no
> internet access. We do not collect, store on a server, sell or share any personal information.
>
> **Camera.** The app asks for permission to use your camera so it can show the live view and take
> photos. Camera images are processed on your device and are not sent anywhere.
>
> **Photos.** When you tap the shutter or Save, the app saves a photo to the Pictures/AniVision
> folder on your device. When you import a photo, the app reads only the photo you pick, using
> Android's system picker. The photos stay on your device. If you tap Share, the photo goes to the
> app you choose in Android's share sheet; what that app does with it is covered by its own policy.
>
> **Stored settings.** The app remembers which two views you chose on your device. Nothing else is
> stored, and app backup is turned off.
>
> **Children.** The app does not knowingly collect information from anyone, including children.
>
> **Changes.** If this policy changes, the new version will be posted at this address with a new
> date.
>
> **Contact.** [email address]
