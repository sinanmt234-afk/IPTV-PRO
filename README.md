# Aether IPTV (Android TV)

Controls (while playing): UP = next channel, DOWN = previous channel, OK/LEFT/RIGHT = channel list, 0-9 = jump to channel number, BACK = close list / exit.

## APK GitHub-il build cheyyan
1. github.com -> New repository (public/private, any name) -> create.
2. Ee folder-ile ellaa files-um (hidden `.github` folder ulppede) upload cheyyuka (Add file -> Upload files) / git push.
3. Repository -> Actions tab -> "Build APK" -> Run workflow (push cheythaal automatic aayi run aakum).
4. 3-5 minute kazhinjal run open cheythu `AetherIPTV-apk` artifact download cheyyuka -> zip-il `app-debug.apk` undaakum.
5. APK TV-yil install cheyyuka (USB / Send Files to TV / Downloader app).

## EPG (Program Guide)
iptv-org/epg oru ready-made link alla, athu guide.xml undaakkunna tool aanu. Athinu:
1. github.com/iptv-org/epg -> Fork.
2. Fork-inte root-il `channels.xml` undaakkuka (README-yile "How to use" nokki, nammude channels-inte site files `sites/` folder-ilninnu copy cheyyam).
3. `epg-workflow/epg.yml` fork-il `.github/workflows/epg.yml` aayi add cheyyuka.
4. Fork -> Settings -> Pages -> Source: "GitHub Actions". Actions-il "EPG" workflow run cheyyuka.
5. Link: `https://YOURNAME.github.io/epg/guide.xml` (repo name anusarichu maarum).
6. App project-il `Data.kt`-yile `Config.EPG_URL`-il aa link ittu push cheyyuka -> APK veendum build aakum.
