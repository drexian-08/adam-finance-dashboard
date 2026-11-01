# Firebase Cloud Sync Setup — ADAM Finance

This project uses Firebase Authentication (Email/Password) and Cloud Firestore.
The same email/password account must be used in the web dashboard and Android widget.

## 1. Create the Firebase project
1. Open https://console.firebase.google.com/ and create a project (for example: adam-finance-private).
2. In **Authentication → Sign-in method**, enable **Email/Password**.
3. In **Firestore Database**, create a database. Choose a region close to Malaysia (for example asia-southeast1) and start in production mode.
4. In **Project settings → General**, register a Web app and copy its Firebase web config. The web config contains public project identifiers; never share service-account private keys.
5. Copy the **Web API Key** and **Project ID**. These are needed by both clients.

## 2. Secure Firestore
Publish the contents of `firestore.rules` in this folder in Firebase Console → Firestore Database → Rules.
The rules allow each signed-in user to read/write only their own document:
`users/{uid}/private/finance`.
Do not use open rules such as `allow read, write: if true`.

## 3. Create the account
In Firebase Console → Authentication → Users, click **Add user** and create an email/password account for yourself.
Use the same account on your web dashboard and Android app. Do not send your password to anyone.

## 4. Finish client configuration
The repository intentionally does not contain a Firebase project config yet. Once your Firebase project is created, provide the **Web API Key**, **Project ID**, and web app config fields (apiKey, authDomain, projectId, appId). These are client configuration values, not passwords. Do not provide service-account JSON, private keys, or your login password.

After configuration, the dashboard and Android client can be built to sign in to the same Firebase account and sync the finance document. Local browser data will remain intact; sync should be enabled only after importing/exporting a backup and checking the initial merge.

## Privacy notes
- Firebase stores financial values in a private Firestore document protected by authenticated-user security rules.
- Anyone with your login credentials can access that account. Use a strong unique password and enable available account protections.
- GitHub Pages is public hosting; never place personal financial records or service-account secrets in the repository.
- Cloud sync requires internet. Local data should remain usable offline and sync when connected.
