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
