# הבית שלנו – מסך הבית של הדירה

Launcher לטלוויזיה של הדירה של אוריה, הדס, נועה ונעם: אריה, קפיברה, כלב וארנבת על ירח מתנדנד, מזג אוויר בחיפה עם זריחה ושקיעה, ווידג'ט יומי מתחלף, ואריחים ל-Netflix, סלקום TV, Disney+, HBO Max, HDMI ו"כל האפליקציות".

## מבנה

- `MainActivity.java` – המסך: הסצנה, מזג האוויר, הווידג'ט, האריחים ומגירת כל האפליקציות.
- `DailyContent.java` – בוחר כל יום מילה, ארוחה, עובדה ובדיחה מתוך `assets/content.json`.
- `Weather.java` – מזג אוויר, זריחה ושקיעה מ-Open-Meteo (חינמי, בלי מפתח).
- `HomeWatcherService.java` – מחזיר למסך הזה כשלוחצים Home בשלט.

## התקנה על הטלוויזיה (PowerShell בטרמינל של Android Studio)

```
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb connect <IP-של-הטלוויזיה>:5555
```
לאשר בטלוויזיה, לבחור אותה ברשימת המכשירים וללחוץ ▶ Run.

## כפתור ה-Home

**אל תכבי את מסך הבית המקורי** (`pm disable-user`) – זה משאיר מסך שחור. במקום זה:
```
& $adb shell cmd package set-home-activity com.dudai.roommates/.MainActivity
& $adb shell settings get secure enabled_accessibility_services
```
אם התשובה היא `null`:
```
& $adb shell settings put secure enabled_accessibility_services com.dudai.roommates/com.dudai.roommates.HomeWatcherService
& $adb shell settings put secure accessibility_enabled 1
```
לביטול: `& $adb shell settings delete secure enabled_accessibility_services`

## תוכן יומי

`app/src/main/assets/content.json`: 100 מילים בעברית, 100 באנגלית (לסירוגין), 60 ארוחות, 72 עובדות על החיות ו-50 בדיחות. אפשר להוסיף פריטים באותו מבנה ולהתקין מחדש.

## הגדרות בראש MainActivity.java

- `LAT`, `LON` – מיקום מזג האוויר (חיפה).
- `HDMI_INDEX` – איזו כניסת HDMI האריח פותח (0 = הראשונה).
- `apps` – האריחים: שם, צבע ושמות חבילות.
