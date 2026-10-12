#!/usr/bin/env python3
"""Generates Android res/values*/strings.xml from the desktop .properties bundles.

Keys keep their dotted names with dots turned into underscores, so a desktop
key like popup.describePlaceholder becomes @string/popup_describePlaceholder.
Desktop-only keys (tray menu, global shortcut, Windows credential caption,
account CLI section) are skipped. Android-only strings (process-text menu
label, home/tutorial screen) are defined here for all 11 locales.
"""
import re
from pathlib import Path

KMP = Path(__file__).resolve().parents[2]
SRC = KMP / "desktop/app/src/main/resources"
OUT = KMP / "android/app/src/main/res"

LOCALES = {  # properties suffix -> res qualifier ('' = default English)
    "": "", "_de": "-de", "_es": "-es", "_fr": "-fr", "_it": "-it",
    "_ja": "-ja", "_ko": "-ko", "_pl": "-pl", "_pt": "-pt", "_uk": "-uk", "_zh": "-zh",
}

SKIP_PREFIXES = ("menu.", "shortcut.", "account.")
SKIP_KEYS = {
    "general.startup", "general.launchAtLogin",  # no login-item concept on Android
    "provider.credentialCaption",                # names the Windows Credential Manager
    "popup.noSelection",                         # names the desktop hotkey
    "error.replaceFailed",                       # desktop paste mechanics
}

# lc -> key -> text. Android-only strings, translated to match each bundle's voice.
EXTRA = {
    "": {
        "android.processText": "Reword me",
        "android.keystoreCaption": "Stored encrypted with a key kept in this device's Android Keystore, never in plain files.",
        "home.title": "Welcome to RewordMe",
        "home.description": "Your writing, polished right where you write it. Select text in any app, tap \u201cReword me\u201d in the selection menu, then pick Proofread, Rewrite or a tone \u2013 the improved text replaces the original in place.",
        "home.tryTitle": "Try it now",
        "home.tryHint": "Press and hold the text below to select it, then tap \u201cReword me\u201d in the menu.",
        "home.sample": "hey, just wanted to check if you maybe have time to look at the report i sent over, its not super urgent but i would really apreciate if you could get back to me before friday somehow",
        "home.openSettings": "Settings",
        "home.setupHint": "First, pick a provider and add its API key in Settings.",
        "android.close": "Close",
        "android.back": "Back",
        "android.noSelection": "Nothing to reword yet. Select text in any app, then tap “%s” in the menu.",
        "provider.removeKey": "Remove Key",
        "android.copied": "Copied to clipboard",
        "android.replaceAlsoCopied": "Also copied – if the text didn't change, paste it over the selection.",
        "android.replaceUnavailable": "This app doesn't let RewordMe replace the text. Copy the result and paste it where you need it.",
        "android.selectionTooShort": "The selection is too short. Select a longer piece of text, then try again.",
        "android.errorNetwork": "Couldn't reach the provider. Check your internet connection or the server address in Settings, then try again.",
        "android.errorTimeout": "The provider took too long to answer. Try again in a moment.",
        "android.slowHint": "Still working… Local models and busy providers can take a while.",
        "android.menuHint": "Don't see “Reword me”? Open the ⋮ menu.",
        "android.ollamaPhoneHint": "On a phone, “localhost” means the phone itself. Enter your computer's address on the same Wi-Fi (for example 192.168.1.20:11434) and start Ollama with OLLAMA_HOST=0.0.0.0.",
        "android.shareHint": "Some apps (e.g. Messages, Gmail, Contacts) don't show “Reword me” at all. There, tap Share, pick “Reword me”, then paste the result back.",
    },
    "_de": {
        "android.processText": "Umformulieren",
        "android.keystoreCaption": "Verschl\u00fcsselt gespeichert mit einem Schl\u00fcssel im Android-Keystore dieses Ger\u00e4ts, nie in Klartextdateien.",
        "home.title": "Willkommen bei RewordMe",
        "home.description": "Dein Text, direkt an Ort und Stelle verbessert. Markiere Text in einer beliebigen App, tippe im Auswahlmen\u00fc auf \u201eUmformulieren\u201c und w\u00e4hle Korrektur, Umformulieren oder einen Ton \u2013 der verbesserte Text ersetzt das Original an derselben Stelle.",
        "home.tryTitle": "Probier es gleich aus",
        "home.tryHint": "Halte den Text unten gedr\u00fcckt, um ihn zu markieren, und tippe dann im Men\u00fc auf \u201eUmformulieren\u201c.",
        "home.sample": "hey wollte nur kurz fragen ob du vieleicht zeit hast dir den bericht anzuschauen den ich geschickt hab, ist nicht super dringend aber w\u00e4r echt nett wenn du dich bis freitag irgendwie melden k\u00f6nntest",
        "home.openSettings": "Einstellungen",
        "home.setupHint": "W\u00e4hle zuerst in den Einstellungen einen Anbieter und hinterlege seinen API-Schl\u00fcssel.",
        "android.close": "Schlie\u00dfen",
        "android.back": "Zur\u00fcck",
        "android.noSelection": "Noch nichts zum Umformulieren. Markiere Text in einer beliebigen App und tippe dann im Men\u00fc auf \u201e%s\u201c.",
        "provider.removeKey": "Schl\u00fcssel entfernen",
        "android.copied": "In die Zwischenablage kopiert",
        "android.replaceAlsoCopied": "Auch kopiert – falls sich der Text nicht geändert hat, füge ihn über der Auswahl ein.",
        "android.replaceUnavailable": "Diese App lässt RewordMe den Text nicht ersetzen. Kopiere das Ergebnis und füge es dort ein, wo du es brauchst.",
        "android.selectionTooShort": "Die Auswahl ist zu kurz. Markiere einen längeren Text und versuche es erneut.",
        "android.errorNetwork": "Der Anbieter ist nicht erreichbar. Prüfe deine Internetverbindung oder die Serveradresse in den Einstellungen und versuche es erneut.",
        "android.errorTimeout": "Der Anbieter hat zu lange nicht geantwortet. Versuche es gleich noch einmal.",
        "android.slowHint": "Läuft noch… Lokale Modelle und ausgelastete Anbieter brauchen manchmal etwas länger.",
        "android.menuHint": "Du siehst „Umformulieren“ nicht? Öffne das ⋮-Menü.",
        "android.ollamaPhoneHint": "Auf dem Handy bedeutet „localhost“ das Handy selbst. Gib die Adresse deines Computers im selben WLAN ein (z. B. 192.168.1.20:11434) und starte Ollama mit OLLAMA_HOST=0.0.0.0.",
        "android.shareHint": "Manche Apps (z. B. Messages, Gmail, Kontakte) zeigen „Umformulieren“ gar nicht an. Tippe dort auf „Teilen“, wähle „Umformulieren“ und füge das Ergebnis anschließend ein.",
    },
    "_es": {
        "android.processText": "Reformular",
        "android.keystoreCaption": "Se guarda cifrada con una clave que reside en el Android Keystore de este dispositivo, nunca en archivos de texto plano.",
        "home.title": "Te damos la bienvenida a RewordMe",
        "home.description": "Tu texto, mejorado ah\u00ed mismo. Selecciona texto en cualquier app, toca \u00abReformular\u00bb en el men\u00fa de selecci\u00f3n y elige Corregir, Reformular o un tono: el texto mejorado sustituye al original justo donde estaba.",
        "home.tryTitle": "Pru\u00e9balo ahora",
        "home.tryHint": "Mant\u00e9n pulsado el texto de abajo para seleccionarlo y toca \u00abReformular\u00bb en el men\u00fa.",
        "home.sample": "hola, solo queria saber si igual tienes un rato para mirar el informe que te mand\u00e9, no es muy urgente pero te agradeceria mucho si me pudieras contestar antes del viernes de alguna forma",
        "home.openSettings": "Ajustes",
        "home.setupHint": "Primero elige un proveedor y a\u00f1ade su clave de API en Ajustes.",
        "android.close": "Cerrar",
        "android.back": "Atr\u00e1s",
        "android.noSelection": "Todav\u00eda no hay nada que reformular. Selecciona texto en cualquier app y toca \u00ab%s\u00bb en el men\u00fa.",
        "provider.removeKey": "Eliminar clave",
        "android.copied": "Copiado al portapapeles",
        "android.replaceAlsoCopied": "También se ha copiado: si el texto no ha cambiado, pégalo sobre la selección.",
        "android.replaceUnavailable": "Esta app no permite que RewordMe sustituya el texto. Copia el resultado y pégalo donde lo necesites.",
        "android.selectionTooShort": "La selección es demasiado corta. Selecciona un fragmento más largo y vuelve a intentarlo.",
        "android.errorNetwork": "No se ha podido conectar con el proveedor. Comprueba tu conexión a internet o la dirección del servidor en Ajustes y vuelve a intentarlo.",
        "android.errorTimeout": "El proveedor ha tardado demasiado en responder. Vuelve a intentarlo en un momento.",
        "android.slowHint": "Seguimos trabajando… Los modelos locales y los proveedores saturados pueden tardar un poco.",
        "android.menuHint": "¿No ves «Reformular»? Abre el menú ⋮.",
        "android.ollamaPhoneHint": "En el móvil, «localhost» es el propio móvil. Introduce la dirección de tu ordenador en la misma red wifi (por ejemplo, 192.168.1.20:11434) e inicia Ollama con OLLAMA_HOST=0.0.0.0.",
        "android.shareHint": "Algunas apps (p. ej., Mensajes, Gmail, Contactos) no muestran «Reformular». En ellas, toca Compartir, elige «Reformular» y luego pega el resultado.",
    },
    "_fr": {
        "android.processText": "Reformuler",
        "android.keystoreCaption": "Stock\u00e9e chiffr\u00e9e avec une cl\u00e9 conserv\u00e9e dans l'Android Keystore de cet appareil, jamais en clair dans des fichiers.",
        "home.title": "Bienvenue dans RewordMe",
        "home.description": "Votre texte, am\u00e9lior\u00e9 sur place. S\u00e9lectionnez du texte dans n'importe quelle app, touchez \u00ab\u00a0Reformuler\u00a0\u00bb dans le menu de s\u00e9lection, puis choisissez Corriger, Reformuler ou un ton\u00a0: le texte am\u00e9lior\u00e9 remplace l'original l\u00e0 o\u00f9 il se trouvait.",
        "home.tryTitle": "Essayez maintenant",
        "home.tryHint": "Appuyez longuement sur le texte ci-dessous pour le s\u00e9lectionner, puis touchez \u00ab\u00a0Reformuler\u00a0\u00bb dans le menu.",
        "home.sample": "salut, je voulais juste savoir si tu aurais peut etre le temps de regarder le rapport que je t'ai envoy\u00e9, c'est pas hyper urgent mais ce serait vraimant sympa si tu pouvais me r\u00e9pondre avant vendredi",
        "home.openSettings": "R\u00e9glages",
        "home.setupHint": "Commencez par choisir un fournisseur et ajouter sa cl\u00e9 API dans les R\u00e9glages.",
        "android.close": "Fermer",
        "android.back": "Retour",
        "android.noSelection": "Rien \u00e0 reformuler pour l'instant. S\u00e9lectionnez du texte dans n'importe quelle app, puis touchez \u00ab\u00a0%s\u00a0\u00bb dans le menu.",
        "provider.removeKey": "Supprimer la cl\u00e9",
        "android.copied": "Copié dans le presse-papiers",
        "android.replaceAlsoCopied": "Également copié : si le texte n'a pas changé, collez-le à la place de la sélection.",
        "android.replaceUnavailable": "Cette app ne permet pas à RewordMe de remplacer le texte. Copiez le résultat et collez-le là où vous en avez besoin.",
        "android.selectionTooShort": "La sélection est trop courte. Sélectionnez un passage plus long, puis réessayez.",
        "android.errorNetwork": "Impossible de joindre le fournisseur. Vérifiez votre connexion Internet ou l'adresse du serveur dans les Réglages, puis réessayez.",
        "android.errorTimeout": "Le fournisseur a mis trop de temps à répondre. Réessayez dans un instant.",
        "android.slowHint": "Toujours en cours… Les modèles locaux et les fournisseurs très sollicités peuvent prendre un moment.",
        "android.menuHint": "Vous ne voyez pas « Reformuler » ? Ouvrez le menu ⋮.",
        "android.ollamaPhoneHint": "Sur un téléphone, « localhost » désigne le téléphone lui-même. Saisissez l'adresse de votre ordinateur sur le même Wi-Fi (par exemple 192.168.1.20:11434) et lancez Ollama avec OLLAMA_HOST=0.0.0.0.",
        "android.shareHint": "Certaines apps (p. ex. Messages, Gmail, Contacts) n'affichent pas « Reformuler ». Touchez alors Partager, choisissez « Reformuler », puis collez le résultat.",
    },
    "_it": {
        "android.processText": "Riformula",
        "android.keystoreCaption": "Salvata cifrata con una chiave custodita nell'Android Keystore di questo dispositivo, mai in file in chiaro.",
        "home.title": "Benvenuto in RewordMe",
        "home.description": "Il tuo testo, migliorato sul posto. Seleziona il testo in qualsiasi app, tocca \u00abRiformula\u00bb nel menu di selezione e scegli Correggi, Riformula o un tono: il testo migliorato sostituisce l'originale esattamente dov'era.",
        "home.tryTitle": "Provalo subito",
        "home.tryHint": "Tieni premuto il testo qui sotto per selezionarlo, poi tocca \u00abRiformula\u00bb nel menu.",
        "home.sample": "ciao, volevo solo chiederti se magari hai tempo di dare un'occhiata al report che ti ho mandato, non \u00e8 urgentissimo per\u00f2 mi farebbe molto piacere se riuscissi a rispondermi entro venerdi in qualche modo",
        "home.openSettings": "Impostazioni",
        "home.setupHint": "Per prima cosa scegli un provider e aggiungi la sua chiave API nelle Impostazioni.",
        "android.close": "Chiudi",
        "android.back": "Indietro",
        "android.noSelection": "Non c'è ancora nulla da riformulare. Seleziona il testo in qualsiasi app, poi tocca «%s» nel menu.",
        "provider.removeKey": "Rimuovi chiave",
        "android.copied": "Copiato negli appunti",
        "android.replaceAlsoCopied": "Copiato anche negli appunti: se il testo non è cambiato, incollalo al posto della selezione.",
        "android.replaceUnavailable": "Questa app non permette a RewordMe di sostituire il testo. Copia il risultato e incollalo dove ti serve.",
        "android.selectionTooShort": "La selezione è troppo corta. Seleziona un testo più lungo e riprova.",
        "android.errorNetwork": "Impossibile raggiungere il provider. Controlla la connessione a Internet o l'indirizzo del server nelle Impostazioni, poi riprova.",
        "android.errorTimeout": "Il provider ha impiegato troppo tempo a rispondere. Riprova tra un attimo.",
        "android.slowHint": "Ancora in corso… I modelli locali e i provider sovraccarichi possono richiedere un po' di tempo.",
        "android.menuHint": "Non vedi «Riformula»? Apri il menu ⋮.",
        "android.ollamaPhoneHint": "Sul telefono «localhost» indica il telefono stesso. Inserisci l'indirizzo del computer sulla stessa rete Wi-Fi (ad esempio 192.168.1.20:11434) e avvia Ollama con OLLAMA_HOST=0.0.0.0.",
        "android.shareHint": "Alcune app (ad es. Messaggi, Gmail, Contatti) non mostrano «Riformula». Lì tocca Condividi, scegli «Riformula» e poi incolla il risultato.",
    },
    "_ja": {
        "android.processText": "\u30ea\u30e9\u30a4\u30c8",
        "android.keystoreCaption": "\u3053\u306e\u7aef\u672b\u306e Android Keystore \u306b\u4fdd\u7ba1\u3055\u308c\u305f\u9375\u3067\u6697\u53f7\u5316\u3057\u3066\u4fdd\u5b58\u3055\u308c\u307e\u3059\u3002\u5e73\u6587\u306e\u30d5\u30a1\u30a4\u30eb\u306b\u306f\u4fdd\u5b58\u3055\u308c\u307e\u305b\u3093\u3002",
        "home.title": "RewordMe \u3078\u3088\u3046\u3053\u305d",
        "home.description": "\u66f8\u3044\u305f\u305d\u306e\u5834\u3067\u6587\u7ae0\u3092\u78e8\u3051\u307e\u3059\u3002\u4efb\u610f\u306e\u30a2\u30d7\u30ea\u3067\u30c6\u30ad\u30b9\u30c8\u3092\u9078\u629e\u3057\u3001\u8868\u793a\u3055\u308c\u308b\u30e1\u30cb\u30e5\u30fc\u3067\u300c\u30ea\u30e9\u30a4\u30c8\u300d\u3092\u30bf\u30c3\u30d7\u3057\u3066\u3001\u6821\u6b63\u30fb\u30ea\u30e9\u30a4\u30c8\u30fb\u30c8\u30fc\u30f3\u3092\u9078\u3076\u3060\u3051\u3002\u6539\u5584\u3055\u308c\u305f\u6587\u7ae0\u304c\u5143\u306e\u5834\u6240\u3092\u305d\u306e\u307e\u307e\u7f6e\u304d\u63db\u3048\u307e\u3059\u3002",
        "home.tryTitle": "\u3055\u3063\u305d\u304f\u8a66\u3059",
        "home.tryHint": "\u4e0b\u306e\u30c6\u30ad\u30b9\u30c8\u3092\u9577\u62bc\u3057\u3057\u3066\u9078\u629e\u3057\u3001\u30e1\u30cb\u30e5\u30fc\u306e\u300c\u30ea\u30e9\u30a4\u30c8\u300d\u3092\u30bf\u30c3\u30d7\u3057\u3066\u304f\u3060\u3055\u3044\u3002",
        "home.sample": "\u304a\u3064\u304b\u308c\u3055\u307e\u3067\u3059\u3001\u305b\u3093\u3058\u3064\u304a\u304a\u304f\u308a\u3057\u305f\u30ec\u30dd\u30fc\u30c8\u3067\u3059\u304c\u3001\u3082\u3057\u304a\u6642\u9593\u3042\u308c\u3070\u898b\u3066\u3044\u305f\u3060\u3051\u305f\u3089\u3046\u308c\u3057\u3044\u3067\u3059\u3002\u6025\u304e\u3067\u306f\u306a\u3044\u306e\u3067\u3059\u304c\u3001\u3067\u304d\u308c\u3070\u91d1\u66dc\u307e\u3067\u306b\u304a\u8fd4\u4e8b\u3044\u305f\u3060\u3051\u308b\u3068\u305f\u3059\u304b\u308a\u307e\u3059",
        "home.openSettings": "\u8a2d\u5b9a",
        "home.setupHint": "\u307e\u305a\u8a2d\u5b9a\u3067\u30d7\u30ed\u30d0\u30a4\u30c0\u30fc\u3092\u9078\u3073\u3001API \u30ad\u30fc\u3092\u8ffd\u52a0\u3057\u3066\u304f\u3060\u3055\u3044\u3002",
        "android.close": "閉じる",
        "android.back": "戻る",
        "android.noSelection": "リライトするテキストがありません。任意のアプリでテキストを選択し、メニューの「%s」をタップしてください。",
        "provider.removeKey": "キーを削除",
        "android.copied": "クリップボードにコピーしました",
        "android.replaceAlsoCopied": "コピーもしました。テキストが置き換わっていない場合は、選択範囲に貼り付けてください。",
        "android.replaceUnavailable": "このアプリでは RewordMe がテキストを置き換えられません。結果をコピーして、必要な場所に貼り付けてください。",
        "android.selectionTooShort": "選択したテキストが短すぎます。もう少し長く選択してから、もう一度お試しください。",
        "android.errorNetwork": "プロバイダーに接続できませんでした。インターネット接続または設定のサーバーアドレスを確認して、もう一度お試しください。",
        "android.errorTimeout": "プロバイダーの応答に時間がかかりすぎました。少し待ってからもう一度お試しください。",
        "android.slowHint": "処理中です… ローカルモデルや混雑しているプロバイダーでは時間がかかることがあります。",
        "android.menuHint": "「リライト」が見つからない場合は、⋮ メニューを開いてください。",
        "android.ollamaPhoneHint": "スマートフォンでは「localhost」はスマートフォン自体を指します。同じ Wi-Fi 上のパソコンのアドレス（例: 192.168.1.20:11434）を入力し、Ollama を OLLAMA_HOST=0.0.0.0 で起動してください。",
        "android.shareHint": "一部のアプリ（メッセージ、Gmail、連絡帳など）では「リライト」が表示されません。その場合は［共有］から「リライト」を選び、結果を貼り付けてください。",
    },
    "_ko": {
        "android.processText": "\ub2e4\ub4ec\uae30",
        "android.keystoreCaption": "\uc774 \uae30\uae30\uc758 Android Keystore\uc5d0 \ubcf4\uad00\ub41c \ud0a4\ub85c \uc554\ud638\ud654\ud574 \uc800\uc7a5\ub418\uba70, \uc77c\ubc18 \ud30c\uc77c\uc5d0\ub294 \uc800\uc7a5\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4.",
        "home.title": "RewordMe\uc5d0 \uc624\uc2e0 \uac83\uc744 \ud658\uc601\ud569\ub2c8\ub2e4",
        "home.description": "\uc4f0\ub358 \uc790\ub9ac\uc5d0\uc11c \ubc14\ub85c \uae00\uc744 \ub2e4\ub4ec\uc5b4 \ubcf4\uc138\uc694. \uc544\ubb34 \uc571\uc5d0\uc11c\ub098 \ud14d\uc2a4\ud2b8\ub97c \uc120\ud0dd\ud558\uace0 \uc120\ud0dd \uba54\ub274\uc5d0\uc11c '\ub2e4\ub4ec\uae30'\ub97c \ud0ed\ud55c \ub4a4 \uad50\uc815, \ub2e4\ub4ec\uae30 \ub610\ub294 \uc5b4\uc870\ub97c \uace0\ub974\uba74 \ub429\ub2c8\ub2e4. \ub2e4\ub4ec\uc5b4\uc9c4 \ud14d\uc2a4\ud2b8\uac00 \uc6d0\ub798 \uc790\ub9ac\uc5d0 \uadf8\ub300\ub85c \ub4e4\uc5b4\uac11\ub2c8\ub2e4.",
        "home.tryTitle": "\uc9c0\uae08 \ud574\ubcf4\uae30",
        "home.tryHint": "\uc544\ub798 \ud14d\uc2a4\ud2b8\ub97c \uae38\uac8c \ub20c\ub7ec \uc120\ud0dd\ud55c \ub2e4\uc74c \uba54\ub274\uc5d0\uc11c '\ub2e4\ub4ec\uae30'\ub97c \ud0ed\ud558\uc138\uc694.",
        "home.sample": "\uc548\ub155\ud558\uc138\uc694, \uc9c0\ub09c\ubc88\uc5d0 \ubcf4\ub0b4\ub4dc\ub9b0 \ubcf4\uace0\uc11c \ud639\uc2dc \uc2dc\uac04 \ub418\uc2e4\ub54c \ud55c\ubc88 \ubd10\uc8fc\uc2e4\uc218 \uc788\uc744\uae4c\uc694, \ub9ce\uc774 \uae09\ud55c\uac74 \uc544\ub2cc\ub370 \uac00\ub2a5\ud558\uba74 \uae08\uc694\uc77c \uc804\uae4c\uc9c0 \ub2f5 \uc8fc\uc2dc\uba74 \uc815\ub9d0 \uac10\uc0ac\ud558\uaca0\uc2b5\ub2c8\ub2e4",
        "home.openSettings": "\uc124\uc815",
        "home.setupHint": "\uba3c\uc800 \uc124\uc815\uc5d0\uc11c \uc81c\uacf5\uc5c5\uccb4\ub97c \uc120\ud0dd\ud558\uace0 API \ud0a4\ub97c \ucd94\uac00\ud558\uc138\uc694.",
        "android.close": "닫기",
        "android.back": "뒤로",
        "android.noSelection": "아직 다듬을 텍스트가 없습니다. 아무 앱에서나 텍스트를 선택한 다음 메뉴에서 '%s'를 탭하세요.",
        "provider.removeKey": "키 삭제",
        "android.copied": "클립보드에 복사했습니다",
        "android.replaceAlsoCopied": "복사도 해 두었습니다. 텍스트가 바뀌지 않았다면 선택한 부분에 붙여넣으세요.",
        "android.replaceUnavailable": "이 앱에서는 RewordMe가 텍스트를 바꿀 수 없습니다. 결과를 복사해 필요한 곳에 붙여넣으세요.",
        "android.selectionTooShort": "선택한 텍스트가 너무 짧습니다. 조금 더 길게 선택한 뒤 다시 시도하세요.",
        "android.errorNetwork": "제공업체에 연결할 수 없습니다. 인터넷 연결이나 설정의 서버 주소를 확인한 뒤 다시 시도하세요.",
        "android.errorTimeout": "제공업체의 응답이 너무 오래 걸렸습니다. 잠시 후 다시 시도하세요.",
        "android.slowHint": "아직 처리 중입니다… 로컬 모델이나 붐비는 제공업체는 시간이 걸릴 수 있습니다.",
        "android.menuHint": "'다듬기'가 보이지 않나요? ⋮ 메뉴를 열어 보세요.",
        "android.ollamaPhoneHint": "휴대폰에서 'localhost'는 휴대폰 자신을 뜻합니다. 같은 Wi-Fi에 있는 컴퓨터 주소(예: 192.168.1.20:11434)를 입력하고 Ollama를 OLLAMA_HOST=0.0.0.0으로 실행하세요.",
        "android.shareHint": "일부 앱(예: 메시지, Gmail, 연락처)에는 '다듬기'가 표시되지 않습니다. 이때는 공유를 탭하고 '다듬기'를 선택한 뒤 결과를 붙여넣으세요.",
    },
    "_pl": {
        "android.processText": "Przeredaguj",
        "android.keystoreCaption": "Przechowywany w postaci zaszyfrowanej kluczem z Android Keystore tego urz\u0105dzenia, nigdy w zwyk\u0142ych plikach.",
        "home.title": "Witaj w RewordMe",
        "home.description": "Tw\u00f3j tekst, poprawiony tam, gdzie go piszesz. Zaznacz tekst w dowolnej aplikacji, w menu zaznaczania stuknij \u201ePrzeredaguj\u201d, a potem wybierz korekt\u0119, przeredagowanie albo ton \u2013 poprawiony tekst zast\u0105pi orygina\u0142 dok\u0142adnie w tym samym miejscu.",
        "home.tryTitle": "Wypr\u00f3buj teraz",
        "home.tryHint": "Przytrzymaj poni\u017cszy tekst, aby go zaznaczy\u0107, a nast\u0119pnie stuknij \u201ePrzeredaguj\u201d w menu.",
        "home.sample": "hej, chcia\u0142em tylko zapyta\u0107 czy mia\u0142bys mo\u017ce chwil\u0119 \u017ceby rzuci\u0107 okiem na raport kt\u00f3ry ci wys\u0142a\u0142em, to nie jest jako\u015b super pilne ale by\u0142bym wdzieczny gdyby\u015b da\u0142 zna\u0107 jako\u015b przed pi\u0105tkiem",
        "home.openSettings": "Ustawienia",
        "home.setupHint": "Najpierw wybierz dostawc\u0119 i dodaj jego klucz API w Ustawieniach.",
        "android.close": "Zamknij",
        "android.back": "Wstecz",
        "android.noSelection": "Nie ma jeszcze nic do przeredagowania. Zaznacz tekst w dowolnej aplikacji, a następnie stuknij „%s” w menu.",
        "provider.removeKey": "Usuń klucz",
        "android.copied": "Skopiowano do schowka",
        "android.replaceAlsoCopied": "Skopiowano też do schowka – jeśli tekst się nie zmienił, wklej go w miejsce zaznaczenia.",
        "android.replaceUnavailable": "Ta aplikacja nie pozwala RewordMe podmienić tekstu. Skopiuj wynik i wklej go tam, gdzie potrzebujesz.",
        "android.selectionTooShort": "Zaznaczenie jest za krótkie. Zaznacz dłuższy fragment i spróbuj ponownie.",
        "android.errorNetwork": "Nie udało się połączyć z dostawcą. Sprawdź połączenie z internetem lub adres serwera w Ustawieniach i spróbuj ponownie.",
        "android.errorTimeout": "Dostawca zbyt długo nie odpowiadał. Spróbuj ponownie za chwilę.",
        "android.slowHint": "Wciąż pracuję… Modele lokalne i przeciążeni dostawcy potrzebują czasem więcej czasu.",
        "android.menuHint": "Nie widzisz „Przeredaguj”? Otwórz menu ⋮.",
        "android.ollamaPhoneHint": "Na telefonie „localhost” oznacza sam telefon. Wpisz adres komputera w tej samej sieci Wi-Fi (np. 192.168.1.20:11434) i uruchom Ollamę z OLLAMA_HOST=0.0.0.0.",
        "android.shareHint": "Niektóre aplikacje (np. Wiadomości, Gmail, Kontakty) w ogóle nie pokazują „Przeredaguj”. Tam stuknij „Udostępnij”, wybierz „Przeredaguj”, a potem wklej wynik.",
    },
    "_pt": {
        "android.processText": "Reformular",
        "android.keystoreCaption": "Guardada cifrada com uma chave mantida no Android Keystore deste aparelho, nunca em arquivos de texto simples.",
        "home.title": "Boas-vindas ao RewordMe",
        "home.description": "Seu texto, melhorado ali mesmo. Selecione um texto em qualquer app, toque em \u201cReformular\u201d no menu de sele\u00e7\u00e3o e escolha Revisar, Reformular ou um tom: o texto melhorado substitui o original exatamente onde estava.",
        "home.tryTitle": "Experimente agora",
        "home.tryHint": "Toque e segure o texto abaixo para selecion\u00e1-lo e depois toque em \u201cReformular\u201d no menu.",
        "home.sample": "oi, s\u00f3 queria saber se voce talvez tem um tempo pra olhar o relat\u00f3rio que te mandei, n\u00e3o \u00e9 super urgente mas eu agradeceria muito se pudesse me responder antes de sexta de algum jeito",
        "home.openSettings": "Ajustes",
        "home.setupHint": "Primeiro escolha um provedor e adicione a chave de API dele nos Ajustes.",
        "android.close": "Fechar",
        "android.back": "Voltar",
        "android.noSelection": "Ainda não há nada para reformular. Selecione um texto em qualquer app e toque em “%s” no menu.",
        "provider.removeKey": "Remover chave",
        "android.copied": "Copiado para a área de transferência",
        "android.replaceAlsoCopied": "Também foi copiado: se o texto não mudou, cole-o sobre a seleção.",
        "android.replaceUnavailable": "Este app não permite que o RewordMe substitua o texto. Copie o resultado e cole onde precisar.",
        "android.selectionTooShort": "A seleção é curta demais. Selecione um trecho maior e tente de novo.",
        "android.errorNetwork": "Não foi possível conectar ao provedor. Verifique sua conexão com a internet ou o endereço do servidor nos Ajustes e tente de novo.",
        "android.errorTimeout": "O provedor demorou demais para responder. Tente de novo daqui a pouco.",
        "android.slowHint": "Ainda trabalhando… Modelos locais e provedores sobrecarregados podem levar um tempo.",
        "android.menuHint": "Não está vendo “Reformular”? Abra o menu ⋮.",
        "android.ollamaPhoneHint": "No celular, “localhost” é o próprio celular. Digite o endereço do seu computador na mesma rede Wi-Fi (por exemplo, 192.168.1.20:11434) e inicie o Ollama com OLLAMA_HOST=0.0.0.0.",
        "android.shareHint": "Alguns apps (como Mensagens, Gmail e Contatos) não mostram “Reformular”. Neles, toque em Compartilhar, escolha “Reformular” e depois cole o resultado.",
    },
    "_uk": {
        "android.processText": "\u041f\u0435\u0440\u0435\u0444\u043e\u0440\u043c\u0443\u043b\u044e\u0432\u0430\u0442\u0438",
        "android.keystoreCaption": "\u0417\u0431\u0435\u0440\u0456\u0433\u0430\u0454\u0442\u044c\u0441\u044f \u0437\u0430\u0448\u0438\u0444\u0440\u043e\u0432\u0430\u043d\u0438\u043c \u043a\u043b\u044e\u0447\u0435\u043c \u0456\u0437 Android Keystore \u0446\u044c\u043e\u0433\u043e \u043f\u0440\u0438\u0441\u0442\u0440\u043e\u044e, \u043d\u0456\u043a\u043e\u043b\u0438 \u0443 \u0437\u0432\u0438\u0447\u0430\u0439\u043d\u0438\u0445 \u0444\u0430\u0439\u043b\u0430\u0445.",
        "home.title": "\u0412\u0456\u0442\u0430\u0454\u043c\u043e \u0432 RewordMe",
        "home.description": "\u0412\u0430\u0448 \u0442\u0435\u043a\u0441\u0442, \u043f\u043e\u043a\u0440\u0430\u0449\u0435\u043d\u0438\u0439 \u0442\u0430\u043c, \u0434\u0435 \u0432\u0438 \u043f\u0438\u0448\u0435\u0442\u0435. \u0412\u0438\u0434\u0456\u043b\u0456\u0442\u044c \u0442\u0435\u043a\u0441\u0442 \u0443 \u0431\u0443\u0434\u044c-\u044f\u043a\u043e\u043c\u0443 \u0437\u0430\u0441\u0442\u043e\u0441\u0443\u043d\u043a\u0443, \u0442\u043e\u0440\u043a\u043d\u0456\u0442\u044c\u0441\u044f \u00ab\u041f\u0435\u0440\u0435\u0444\u043e\u0440\u043c\u0443\u043b\u044e\u0432\u0430\u0442\u0438\u00bb \u0432 \u043c\u0435\u043d\u044e \u0432\u0438\u0434\u0456\u043b\u0435\u043d\u043d\u044f \u0439 \u0432\u0438\u0431\u0435\u0440\u0456\u0442\u044c \u043a\u043e\u0440\u0435\u043a\u0442\u0443\u0440\u0443, \u043f\u0435\u0440\u0435\u0444\u043e\u0440\u043c\u0443\u043b\u044e\u0432\u0430\u043d\u043d\u044f \u0430\u0431\u043e \u0442\u043e\u043d \u2013 \u043f\u043e\u043a\u0440\u0430\u0449\u0435\u043d\u0438\u0439 \u0442\u0435\u043a\u0441\u0442 \u0437\u0430\u043c\u0456\u043d\u0438\u0442\u044c \u043e\u0440\u0438\u0433\u0456\u043d\u0430\u043b \u0441\u0430\u043c\u0435 \u0442\u0430\u043c, \u0434\u0435 \u0432\u0456\u043d \u0431\u0443\u0432.",
        "home.tryTitle": "\u0421\u043f\u0440\u043e\u0431\u0443\u0439\u0442\u0435 \u0437\u0430\u0440\u0430\u0437",
        "home.tryHint": "\u0423\u0442\u0440\u0438\u043c\u0443\u0439\u0442\u0435 \u0442\u0435\u043a\u0441\u0442 \u043d\u0438\u0436\u0447\u0435, \u0449\u043e\u0431 \u0432\u0438\u0434\u0456\u043b\u0438\u0442\u0438 \u0439\u043e\u0433\u043e, \u0430 \u043f\u043e\u0442\u0456\u043c \u0442\u043e\u0440\u043a\u043d\u0456\u0442\u044c\u0441\u044f \u00ab\u041f\u0435\u0440\u0435\u0444\u043e\u0440\u043c\u0443\u043b\u044e\u0432\u0430\u0442\u0438\u00bb \u0443 \u043c\u0435\u043d\u044e.",
        "home.sample": "\u043f\u0440\u0438\u0432\u0456\u0442, \u0445\u043e\u0442\u0456\u0432 \u043b\u0438\u0448\u0435 \u0441\u043f\u0438\u0442\u0430\u0442\u0438 \u0447\u0438 \u043d\u0435 \u043c\u0430\u0432 \u0431\u0438 \u0442\u0438 \u0447\u0430\u0441\u043e\u043c \u0437\u043c\u043e\u0433\u0438 \u0433\u043b\u044f\u043d\u0443\u0442\u0438 \u043d\u0430 \u0437\u0432\u0456\u0442 \u044f\u043a\u0438\u0439 \u044f \u043d\u0430\u0434\u0456\u0441\u043b\u0430\u0432, \u0446\u0435 \u043d\u0435 \u0434\u0443\u0436\u0435 \u0442\u0435\u0440\u043c\u0456\u043d\u043e\u0432\u043e \u0430\u043b\u0435 \u044f \u0431\u0443\u0432 \u0431\u0438 \u0434\u0443\u0436\u0435 \u0432\u0434\u044f\u0447\u043d\u0438\u0439 \u044f\u043a\u0431\u0438 \u0442\u0438 \u044f\u043a\u043e\u0441\u044c \u0432\u0456\u0434\u043f\u043e\u0432\u0456\u0432 \u0434\u043e \u043f\u044f\u0442\u043d\u0438\u0446\u0456",
        "home.openSettings": "\u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u0438",
        "home.setupHint": "\u0421\u043f\u043e\u0447\u0430\u0442\u043a\u0443 \u0432\u0438\u0431\u0435\u0440\u0456\u0442\u044c \u043f\u0440\u043e\u0432\u0430\u0439\u0434\u0435\u0440\u0430 \u0442\u0430 \u0434\u043e\u0434\u0430\u0439\u0442\u0435 \u0439\u043e\u0433\u043e API-\u043a\u043b\u044e\u0447 \u0443 \u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u0430\u0445.",
        "android.close": "Закрити",
        "android.back": "Назад",
        "android.noSelection": "Поки що немає що переформулювати. Виділіть текст у будь-якому застосунку, а потім торкніться «%s» у меню.",
        "provider.removeKey": "Видалити ключ",
        "android.copied": "Скопійовано в буфер обміну",
        "android.replaceAlsoCopied": "Також скопійовано – якщо текст не змінився, вставте його замість виділення.",
        "android.replaceUnavailable": "Цей застосунок не дозволяє RewordMe замінити текст. Скопіюйте результат і вставте його туди, де потрібно.",
        "android.selectionTooShort": "Виділення надто коротке. Виділіть довший фрагмент і спробуйте ще раз.",
        "android.errorNetwork": "Не вдалося звʼязатися з провайдером. Перевірте підключення до інтернету або адресу сервера в Параметрах і спробуйте ще раз.",
        "android.errorTimeout": "Провайдер надто довго не відповідав. Спробуйте ще раз за мить.",
        "android.slowHint": "Ще працюю… Локальні моделі та перевантажені провайдери можуть відповідати довше.",
        "android.menuHint": "Не бачите «Переформулювати»? Відкрийте меню ⋮.",
        "android.ollamaPhoneHint": "На телефоні «localhost» означає сам телефон. Введіть адресу компʼютера в тій самій мережі Wi-Fi (наприклад, 192.168.1.20:11434) і запустіть Ollama з OLLAMA_HOST=0.0.0.0.",
        "android.shareHint": "Деякі застосунки (наприклад, Повідомлення, Gmail, Контакти) взагалі не показують «Переформулювати». У них торкніться «Поділитися», виберіть «Переформулювати», а потім вставте результат.",
    },
    "_zh": {
        "android.processText": "\u6539\u5199",
        "android.keystoreCaption": "\u4f7f\u7528\u4fdd\u5b58\u5728\u672c\u673a Android Keystore \u4e2d\u7684\u5bc6\u94a5\u52a0\u5bc6\u5b58\u50a8\uff0c\u7edd\u4e0d\u5199\u5165\u660e\u6587\u6587\u4ef6\u3002",
        "home.title": "\u6b22\u8fce\u4f7f\u7528 RewordMe",
        "home.description": "\u5728\u4f60\u5199\u4f5c\u7684\u5730\u65b9\u76f4\u63a5\u6da6\u8272\u6587\u5b57\u3002\u5728\u4efb\u610f\u5e94\u7528\u4e2d\u9009\u4e2d\u6587\u672c\uff0c\u5728\u5f39\u51fa\u7684\u83dc\u5355\u4e2d\u70b9\u6309\u201c\u6539\u5199\u201d\uff0c\u518d\u9009\u62e9\u6821\u5bf9\u3001\u6539\u5199\u6216\u8bed\u6c14\uff0c\u6539\u597d\u7684\u6587\u5b57\u4f1a\u539f\u5730\u66ff\u6362\u6240\u9009\u5185\u5bb9\u3002",
        "home.tryTitle": "\u7acb\u5373\u8bd5\u4e00\u8bd5",
        "home.tryHint": "\u957f\u6309\u9009\u4e2d\u4e0b\u9762\u7684\u6587\u5b57\uff0c\u7136\u540e\u5728\u83dc\u5355\u4e2d\u70b9\u6309\u201c\u6539\u5199\u201d\u3002",
        "home.sample": "\u4f60\u597d\uff0c\u5c31\u662f\u60f3\u95ee\u4e00\u4e0b\u4f60\u6709\u6ca1\u6709\u65f6\u95f4\u770b\u770b\u6211\u53d1\u7ed9\u4f60\u7684\u62a5\u544a\uff0c\u4e0d\u662f\u7279\u522b\u6025\uff0c\u4f46\u8981\u662f\u80fd\u5728\u5468\u4e94\u4e4b\u524d\u7ed9\u6211\u4e2a\u56de\u590d\u5c31\u592a\u611f\u8c22\u5566",
        "home.openSettings": "\u8bbe\u7f6e",
        "home.setupHint": "\u8bf7\u5148\u5728\u8bbe\u7f6e\u4e2d\u9009\u62e9\u670d\u52a1\u5546\u5e76\u6dfb\u52a0\u5176 API \u5bc6\u94a5\u3002",
        "android.close": "关闭",
        "android.back": "返回",
        "android.noSelection": "还没有可改写的文字。请在任意应用中选中文本，然后在菜单中点按“%s”。",
        "provider.removeKey": "移除密钥",
        "android.copied": "已复制到剪贴板",
        "android.replaceAlsoCopied": "也已复制：如果文字没有变化，请粘贴到选中内容处。",
        "android.replaceUnavailable": "此应用不允许 RewordMe 替换文字。请复制结果，然后粘贴到需要的位置。",
        "android.selectionTooShort": "选中的文字太短。请选中更长的内容后重试。",
        "android.errorNetwork": "无法连接到服务商。请检查网络连接或设置中的服务器地址，然后重试。",
        "android.errorTimeout": "服务商响应时间过长。请稍后重试。",
        "android.slowHint": "仍在处理中…本地模型和繁忙的服务商可能需要更长时间。",
        "android.menuHint": "没看到“改写”？请打开 ⋮ 菜单。",
        "android.ollamaPhoneHint": "在手机上，“localhost”指的是手机本身。请输入同一 Wi-Fi 下电脑的地址（例如 192.168.1.20:11434），并使用 OLLAMA_HOST=0.0.0.0 启动 Ollama。",
        "android.shareHint": "部分应用（如信息、Gmail、通讯录）根本不显示“改写”。在这些应用中，请点按“分享”并选择“改写”，然后粘贴结果。",
    },
}


def parse_properties(path: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    pending = ""
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = pending + raw
        pending = ""
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        if line.endswith("\\") and not line.endswith("\\\\"):
            pending = line[:-1]
            continue
        key, _, value = line.partition("=")
        entries[key.strip()] = decode_escapes(value)
    assert not pending, f"dangling continuation in {path}"
    return entries


def decode_escapes(value: str) -> str:
    out, i = [], 0
    while i < len(value):
        ch = value[i]
        if ch == "\\" and i + 1 < len(value):
            nxt = value[i + 1]
            if nxt == "n":
                out.append("\n")
            elif nxt == "t":
                out.append("\t")
            elif nxt == "u":
                out.append(chr(int(value[i + 2:i + 6], 16)))
                i += 6
                continue
            else:
                out.append(nxt)
            i += 2
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def to_android(value: str) -> str:
    # Positional format args: Android requires %1$s once a string is formatted.
    counter = 0

    def positional(match: re.Match) -> str:
        nonlocal counter
        counter += 1
        return f"%{counter}${match.group(1)}"

    converted = re.sub(r"%([sd])", positional, value)
    leftovers = re.sub(r"%\d+\$[sd]", "", converted)
    assert "%" not in leftovers, f"unhandled % in: {value!r}"

    converted = (
        converted.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    )
    return converted


def resource_name(key: str) -> str:
    return key.replace(".", "_")


def main() -> None:
    for suffix, qualifier in LOCALES.items():
        bundle = parse_properties(SRC / f"Strings{suffix}.properties")
        keys = [
            key for key in bundle
            if not key.startswith(SKIP_PREFIXES) and key not in SKIP_KEYS
        ]
        extra = EXTRA[suffix]
        missing = set(EXTRA[""]) - set(extra)
        assert not missing, f"{suffix or 'en'} misses extra keys: {missing}"

        lines = ['<?xml version="1.0" encoding="utf-8"?>']
        lines.append("<!-- Generated from the desktop Strings%s.properties bundle" % suffix)
        lines.append("     by android/tools/gen_strings.py - edit there, then regenerate. -->")
        lines.append("<resources>")
        if suffix == "":
            lines.append('    <string name="app_name" translatable="false">RewordMe</string>')
        for key in keys:
            lines.append(
                f'    <string name="{resource_name(key)}">{to_android(bundle[key])}</string>'
            )
        for key in sorted(extra):
            lines.append(
                f'    <string name="{resource_name(key)}">{to_android(extra[key])}</string>'
            )
        lines.append("</resources>")

        out_dir = OUT / f"values{qualifier}"
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / "strings.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"values{qualifier}: {len(keys) + len(extra)} strings")


if __name__ == "__main__":
    main()
