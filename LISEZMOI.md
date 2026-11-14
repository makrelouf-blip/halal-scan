# Halal Scan – application Android

Scanne le code-barres d'un produit, récupère ses ingrédients sur Open Food Facts (base mise à jour en continu) et indique : ✅ halal / probablement halal, ⚠️ douteux ou ⛔ non halal.

## Récupérer l'APK

À chaque modification, GitHub compile l'application tout seul (onglet **Actions**, environ 5 minutes).
L'APK se télécharge ensuite dans l'onglet **Releases** : ouvre-le sur le téléphone, autorise « installer des applis inconnues », puis installe.

## Mettre à jour les règles halal sans réinstaller

Modifie `app/src/main/assets/www/rules.json` et augmente le numéro `"version"`.
L'appli télécharge la nouvelle version au démarrage (le dépôt doit être **public** pour ça).

## Contenu

- `app/src/main/java/fr/halalscan/app/MainActivity.java` : l'application Android (caméra, internet).
- `app/src/main/assets/www/index.html` : l'interface et l'analyse.
- `app/src/main/assets/www/rules.json` : ingrédients et additifs haram / douteux.
- Le lecteur de code-barres (html5-qrcode, licence Apache 2.0) est ajouté pendant la compilation.

## Limites

L'appli repère les ingrédients à risque, elle ne remplace pas une certification halal (AVS, Achahada, Mosquée de Paris, Lyon, Évry…).
