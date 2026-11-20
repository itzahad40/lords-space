# GitHub se APK banana (Android Studio ke bina)

1. github.com par naya repository banao (jaise `lords-space`). Private rakhna chaho to private.
2. ZIP extract karo, us folder mein Command Prompt kholo aur:

       git init
       git add .
       git commit -m "Lord's-Space"
       git branch -M main
       git remote add origin https://github.com/<tumhara-username>/lords-space.git
       git push -u origin main

   (GitHub password maangega to Personal Access Token use karo: Settings > Developer settings > Tokens.)
3. Repo ke **Actions** tab mein "Build APK" chalega (push par apne aap, ya "Run workflow" dabao).
   Build mein lagbhag 8-15 minute lagte hain.
4. Run poora hone par (green tick) us run ko kholo, neeche **Artifacts** mein
   `Lords-Space-debug-apk` download karo. Zip ke andar `app-debug.apk` hai.
5. APK ko phone mein copy karke install karo.

Build fail ho to run ke red step par click karke error text copy karke mujhe bhej do.
