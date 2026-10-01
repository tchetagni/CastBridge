"""Informatique (L1, L2, L3) : culture numérique, architecture, systèmes, réseaux, bases de données, programmation,
structures de données, complexité, génie logiciel, sécurité, IA, web, compilation.
Faits écrits par l'assistant, indépendants d'un langage précis (sauf mention) ; tous `review`."""
from .sup_kit import *      # source, table, classify, mcq, fq, pairs, pick, cap

L1, L2, L3 = "l1-info", "l2-info", "l3-info"
for _id, _t, _k, _c in [
    ("sup-info-l1", "Culture numérique : matériel, logiciel, Internet, sécurité, codage, algorithmique", "cours de L1 informatique", "Comparer avec un manuel d'initiation à l'informatique et le programme de L1."),
    ("sup-info-l2", "Architecture, systèmes, réseaux, bases de données, programmation", "cours de L2 informatique", "Comparer avec les manuels de Tanenbaum (systèmes, réseaux), Silberschatz (bases de données) et un cours de programmation."),
    ("sup-info-l3", "Structures de données, complexité, génie logiciel, sécurité, IA, web, compilation", "cours de L3 informatique", "Comparer avec Cormen et al. (algorithmique), un manuel de génie logiciel, Stallings (sécurité), Russell et Norvig (IA)."),
]:
    source(_id, _t, _k, _c)

# =====================================================================================================================
# L1
# =====================================================================================================================
S = "sup-info-l1"

table(L1, "inf1-compo", [
    ("le processeur (CPU)", "exécute les instructions des programmes", 1),
    ("la mémoire vive (RAM)", "stocke temporairement les programmes et données en cours d'utilisation", 1),
    ("le disque dur (HDD)", "conserve durablement les données sur des plateaux magnétiques tournants", 2),
    ("le disque SSD", "conserve durablement les données avec de la mémoire flash, sans pièce mobile", 2),
    ("la carte mère", "relie entre eux les composants de l'ordinateur et les fait communiquer", 1),
    ("la carte graphique (GPU)", "calcule l'image à afficher et la transmet à l'écran", 2),
    ("le bloc d'alimentation", "transforme le courant du secteur en tensions adaptées aux composants", 2),
    ("le système de refroidissement", "évacue la chaleur produite par les composants", 2),
    ("le micrologiciel BIOS ou UEFI", "initialise le matériel au démarrage avant de lancer le système d'exploitation", 3),
    ("la mémoire cache", "petite mémoire très rapide placée au plus près du processeur", 3),
    ("la carte réseau", "permet à l'ordinateur d'échanger des données avec un réseau", 2),
], cat="Matériel", src=S, fwd="Quelle description est correcte pour : {a} ?", rev="Parmi ces termes, lequel {b} ?", expl="{A} {b}.")

mcq(L1, "inf1-mem-m", [
    ("Que devient le contenu de la mémoire vive quand l'ordinateur est éteint ?", "Il est perdu", ["Il est copié automatiquement sur le processeur", "Il est conservé indéfiniment", "Il est envoyé sur Internet", "Il est transformé en fichiers"], "La RAM est une mémoire volatile.", 1),
    ("Quel type de stockage conserve ses données sans alimentation électrique ?", "Un disque SSD", ["La mémoire vive", "Les registres du processeur", "La mémoire cache du processeur"], "Le stockage de masse est non volatil, contrairement à la RAM et aux registres.", 2),
    ("Dans la hiérarchie des mémoires, quelle mémoire est la plus rapide ?", "Les registres du processeur", ["Le disque dur", "Le disque SSD", "La mémoire vive", "Le stockage en ligne"], "Plus on s'éloigne du processeur, plus l'accès est lent mais la capacité grande.", 3),
    ("Quelle mémoire a en général la plus grande capacité à faible coût par octet ?", "Le disque de stockage de masse", ["Les registres", "La mémoire cache", "La mémoire vive du processeur"], "Capacité élevée et prix bas vont avec une vitesse plus faible.", 3),
    ("Pourquoi ajouter de la mémoire vive peut-il accélérer un ordinateur saturé ?", "Parce qu'il y a moins de données à échanger avec le disque, bien plus lent", ["Parce que la RAM calcule à la place du processeur", "Parce que la RAM remplace le disque dur", "Parce que la RAM augmente la vitesse d'Internet", "Parce que la RAM refroidit la machine"], "Quand la RAM manque, le système déplace des données vers le disque.", 4),
    ("Avec quoi lit-on un CD ou un DVD ?", "Un faisceau laser", ["Un aimant", "Une tête d'impression", "Une cellule solaire", "Une antenne wifi"], "Ce sont des supports optiques.", 2),
], cat="Matériel", src=S)

classify(L1, "inf1-perif", {
    "un périphérique d'entrée": ["un clavier", "une souris", "un scanner", "un microphone", "une webcam", "une manette de jeu"],
    "un périphérique de sortie": ["une imprimante", "un vidéoprojecteur", "des haut-parleurs", "un traceur de plans"],
    "un périphérique de stockage": ["une clé USB", "un disque dur externe", "une carte SD", "un DVD"],
    "un périphérique à la fois d'entrée et de sortie": ["un écran tactile", "un casque-micro", "un modem"],
}, cat="Matériel", src=S, fwd="Comment classe-t-on {item} ?", rev="Lequel de ces matériels est classé comme {group} ?", expl="{item} est {group}.", diff=2)

classify(L1, "inf1-logi", {
    "un système d'exploitation": ["Windows", "Linux", "macOS", "Android", "FreeBSD"],
    "un navigateur web": ["Firefox", "Chrome", "Safari", "Opera", "Edge"],
    "un tableur": ["Excel", "LibreOffice Calc", "Google Sheets", "Numbers"],
    "un traitement de texte": ["Word", "LibreOffice Writer", "Pages"],
    "un langage de programmation": ["Python", "Java", "JavaScript", "Ruby", "Pascal"],
    "un client de messagerie": ["Thunderbird", "Outlook", "Apple Mail"],
}, cat="Logiciel", src=S, fwd="À quelle catégorie de logiciels ou de langages appartient {item} ?", rev="Lequel de ces noms désigne {group} ?", expl="{item} est {group}.", diff=1)

table(L1, "inf1-os", [
    ("un système d'exploitation", "gère le matériel et fournit aux applications les services de base", 1),
    ("un pilote (driver)", "permet au système de dialoguer avec un périphérique précis", 2),
    ("une application", "répond à un besoin de l'utilisateur, comme écrire, calculer ou communiquer", 1),
    ("un fichier", "regroupe des données portant un nom et enregistrées sur un support", 1),
    ("un dossier (répertoire)", "sert à ranger et organiser des fichiers", 1),
    ("une extension de fichier", "suffixe du nom qui indique en général le type du contenu", 2),
    ("une interface graphique", "permet d'utiliser la machine avec fenêtres, icônes et pointeur", 1),
    ("une ligne de commande", "permet de donner des ordres au système en tapant du texte", 2),
    ("une mise à jour", "corrige des défauts ou failles, ou apporte des fonctions à un logiciel", 1),
    ("un utilitaire de compression", "réduit la taille de fichiers pour les stocker ou les envoyer", 2),
    ("un gestionnaire de tâches", "affiche les programmes en cours et les ressources qu'ils consomment", 3),
], cat="Logiciel", src=S, fwd="Quelle description est correcte pour : {a} ?", rev="Parmi ces termes, lequel {b} ?", expl="{A} {b}.")

table(L1, "inf1-lic", [
    ("un logiciel libre", "laisse à chacun la liberté de l'utiliser, de l'étudier, de le modifier et de le redistribuer", 2),
    ("un logiciel propriétaire", "est distribué avec des restrictions fixées par son éditeur, sans accès libre au code", 2),
    ("un gratuiciel (freeware)", "est utilisable sans payer mais n'est pas forcément libre", 3),
    ("un partagiciel (shareware)", "est utilisable pour un essai, puis réclame un paiement pour continuer", 3),
    ("le copyleft", "impose que les versions modifiées restent soumises aux mêmes libertés", 4),
    ("la licence GPL", "est une licence libre à copyleft fort, issue du projet GNU", 4),
    ("la licence MIT", "est une licence libre permissive qui autorise presque toute réutilisation", 4),
    ("le code source", "est le texte écrit par les programmeurs avant sa traduction en exécutable", 2),
    ("le domaine public", "regroupe les œuvres dont les droits patrimoniaux ne protègent plus l'usage", 4),
    ("un logiciel commercial", "est vendu ou licencié par son éditeur pour en tirer un revenu", 2),
], cat="Logiciel", src=S, fwd="Quelle description est correcte pour : {a} ?", rev="Parmi ces termes, lequel {b} ?", expl="{A} {b}.")

table(L1, "inf1-net", [
    ("Internet", "est le réseau mondial de réseaux interconnectés par des protocoles communs", 1),
    ("le Web", "est un service fondé sur des pages liées par hyperliens, accessible via Internet", 2),
    ("le courrier électronique", "est un service d'échange de messages qui s'appuie sur Internet", 1),
    ("un navigateur", "est le logiciel qui permet d'afficher et de parcourir les pages web", 1),
    ("une URL", "est l'adresse complète d'une ressource sur le Web", 2),
    ("un hyperlien", "est un élément cliquable qui renvoie vers une autre ressource", 1),
    ("un moteur de recherche", "indexe des pages et retrouve celles qui correspondent à une requête", 2),
    ("un fournisseur d'accès à Internet", "est l'entreprise qui connecte un abonné au réseau", 2),
    ("une adresse IP", "est un identifiant numérique attribué à une machine sur un réseau", 2),
    ("un nom de domaine", "est une adresse lisible comme un nom, associée à des machines", 2),
    ("un serveur", "est une machine ou un logiciel qui fournit un service à des clients", 2),
    ("le téléchargement", "est le transfert d'un fichier depuis une machine distante vers la sienne", 1),
    ("le streaming", "permet de lire un contenu au fur et à mesure de sa réception", 2),
    ("le wifi", "est une technologie de réseau local sans fil", 1),
], cat="Internet et Web", src=S, fwd="Quelle description est correcte pour : {a} ?", rev="Parmi ces termes, lequel {b} ?", expl="{A} {b}.")

table(L1, "inf1-cloud", [
    ("l'informatique en nuage (cloud)", "consiste à utiliser des ressources hébergées sur les serveurs d'un prestataire via Internet", 2),
    ("un logiciel en ligne (SaaS)", "s'utilise depuis un navigateur sans avoir à l'installer sur sa machine", 3),
    ("la synchronisation", "tient à jour les mêmes fichiers sur plusieurs appareils", 2),
    ("un centre de données", "est un site qui abrite de nombreux serveurs et équipements réseau", 3),
    ("l'hébergement web", "met à disposition un serveur qui stocke et diffuse un site", 2),
    ("la visioconférence", "permet un échange audio et vidéo à distance en temps réel", 1),
    ("le travail collaboratif en ligne", "permet à plusieurs personnes de modifier le même document à distance", 2),
], cat="Internet et Web", src=S, fwd="Quelle description est correcte pour : {a} ?", rev="Parmi ces termes, lequel {b} ?", expl="{A} {b}.")

table(L1, "inf1-secu", [
    ("l'hameçonnage (phishing)", "imite un organisme de confiance pour pousser la victime à livrer ses informations", 1),
    ("un virus", "est un programme malveillant qui se propage en s'attachant à d'autres fichiers", 2),
    ("un ver", "est un programme malveillant qui se propage de lui-même par le réseau", 3),
    ("un cheval de Troie", "est un programme d'apparence utile qui cache une fonction malveillante", 2),
    ("un rançongiciel", "chiffre les données de la victime et réclame de l'argent pour les rendre", 2),
    ("un logiciel espion", "collecte à son insu des informations sur l'activité de l'utilisateur", 2),
    ("un enregistreur de frappe (keylogger)", "note les touches pressées, par exemple pour voler des mots de passe", 3),
    ("un pare-feu", "filtre les communications réseau selon des règles de sécurité", 2),
    ("un antivirus", "analyse les fichiers pour détecter et neutraliser des programmes malveillants", 1),
    ("une sauvegarde", "est une copie des données conservée pour pouvoir les restaurer après une perte", 1),
    ("l'authentification à deux facteurs", "demande deux preuves de nature différente pour se connecter", 3),
    ("un gestionnaire de mots de passe", "mémorise des mots de passe distincts dans un coffre protégé", 2),
    ("le spam", "est un courrier électronique non sollicité envoyé en masse", 1),
    ("un VPN", "établit un tunnel chiffré entre la machine et un réseau distant", 3),
    ("l'ingénierie sociale", "manipule les personnes plutôt que les machines pour obtenir un accès ou une information", 4),
], cat="Sécurité de base", src=S, fwd="Quelle description est correcte pour : {a} ?", rev="Parmi ces termes, lequel {b} ?", expl="{A} {b}.")

mcq(L1, "inf1-secu-m", [
    ("Quel mot de passe est le plus robuste, toutes choses égales par ailleurs ?", "Une phrase longue et unique, mêlant mots, chiffres et symboles", ["Le prénom suivi de l'année de naissance", "Le mot « motdepasse » avec un 1 à la fin", "Le même mot de passe court réutilisé partout", "Un mot du dictionnaire écrit en majuscules"], "La longueur et l'unicité comptent plus que les astuces de substitution.", 2),
    ("Pourquoi est-il déconseillé de réutiliser le même mot de passe sur plusieurs sites ?", "Si l'un des sites est piraté, tous les autres comptes deviennent vulnérables", ["Parce que les sites interdisent la réutilisation", "Parce que cela ralentit l'ordinateur", "Parce que le mot de passe s'use avec le temps", "Parce que la mémoire de l'appareil se sature"], "Les identifiants volés sont testés sur d'autres services.", 2),
    ("Vous recevez un courriel urgent de votre « banque » avec un lien pour « confirmer votre compte ». Quel est le bon réflexe ?", "Ne pas cliquer et contacter la banque par ses canaux habituels", ["Cliquer vite pour éviter la suspension", "Répondre en envoyant son mot de passe", "Transférer le message à tous ses contacts", "Ouvrir la pièce jointe pour vérifier"], "L'urgence et le lien sont des signes classiques d'hameçonnage.", 1),
    ("Quelle règle de sauvegarde est connue sous le nom « 3-2-1 » ?", "Trois copies, sur deux types de supports, dont une hors site", ["Trois mots de passe, deux facteurs, un antivirus", "Trois sauvegardes par jour, deux par semaine, une par mois", "Trois ordinateurs, deux serveurs, un pare-feu", "Trois disques, deux câbles, une prise"], "Elle réduit le risque de tout perdre en cas de panne, vol ou sinistre.", 4),
    ("Qu'est-ce qui distingue le plus nettement un ver d'un virus classique ?", "Le ver se propage seul par le réseau, sans avoir besoin d'un fichier hôte exécuté par l'utilisateur", ["Le ver ne peut infecter que les téléphones", "Le ver est toujours inoffensif", "Le ver ne se propage jamais par le réseau", "Le ver n'existe que sur les supports optiques"], "Le virus s'attache à un hôte ; le ver s'auto-propage.", 4),
    ("Que prouve le cadenas affiché dans la barre d'adresse d'un navigateur (connexion chiffrée) ?", "La communication avec le site est chiffrée, sans garantir que le site soit honnête", ["Le site est certifié sans danger par l'État", "Le site ne contient aucun virus", "Le site ne collecte aucune donnée", "Le propriétaire du site est une banque"], "Le chiffrement protège le trajet, pas les intentions du site.", 4),
    ("Quel est l'intérêt principal des mises à jour de sécurité ?", "Corriger des failles connues que des attaquants pourraient exploiter", ["Augmenter la capacité du disque", "Changer l'apparence du système", "Supprimer la nécessité d'un mot de passe", "Accélérer la connexion Internet"], "Un système non mis à jour garde des failles publiées.", 2),
    ("Que faire en priorité si votre appareil est infecté par un rançongiciel ?", "Le déconnecter du réseau et restaurer ses données depuis une sauvegarde saine", ["Payer immédiatement la rançon", "Continuer à travailler normalement", "Brancher une clé USB pour copier les fichiers chiffrés", "Prévenir l'auteur de l'attaque"], "Isoler la machine limite la propagation ; la sauvegarde permet de ne pas payer.", 4),
    ("Un SMS vous annonce un gain à un concours auquel vous n'avez pas participé et demande vos coordonnées bancaires. De quoi s'agit-il probablement ?", "D'une tentative d'escroquerie par hameçonnage", ["D'une mise à jour du système", "D'une sauvegarde automatique", "D'une publicité sans risque", "D'un message de l'opérateur de réseau"], "Gain inattendu et demande de données sensibles sont des signaux d'alerte.", 1),
    ("Que fait un pare-feu personnel ?", "Il autorise ou bloque des connexions réseau selon des règles", ["Il répare les fichiers corrompus", "Il compresse les fichiers envoyés", "Il remplace l'antivirus pour toute menace", "Il augmente la vitesse du processeur"], "Il filtre les flux entrants et sortants.", 2),
    ("Quel est le meilleur moyen de se protéger des pertes de données dues à une panne de disque ?", "Faire des sauvegardes régulières sur un autre support", ["Éteindre l'ordinateur chaque soir", "Défragmenter chaque jour", "Utiliser un seul disque plus gros", "Changer de fond d'écran"], "Un seul exemplaire de ses données n'est pas une protection.", 1),
    ("Lequel de ces comportements présente le plus de risque sur un réseau wifi public ?", "Saisir ses identifiants bancaires sur un réseau ouvert non protégé", ["Lire un article d'actualité", "Consulter la météo", "Regarder l'heure", "Écouter de la musique téléchargée"], "Sur un réseau ouvert, les échanges non protégés peuvent être espionnés.", 3),
], cat="Sécurité de base", src=S)

table(L1, "inf1-unit", [
    ("un bit", "est la plus petite unité d'information, qui vaut 0 ou 1", 1),
    ("un octet", "est un groupe de huit bits", 1),
    ("le système binaire", "utilise uniquement les chiffres 0 et 1", 1),
    ("le système hexadécimal", "utilise seize symboles, de 0 à 9 puis de A à F", 3),
    ("le code ASCII", "associe un nombre à chaque caractère d'un jeu de base surtout anglophone", 3),
    ("Unicode", "est une norme qui attribue un numéro à des caractères de presque toutes les écritures", 3),
    ("UTF-8", "est un codage d'Unicode à longueur variable, compatible avec l'ASCII", 4),
    ("un pixel", "est le plus petit point d'une image numérique", 1),
    ("la résolution d'une image", "est le nombre de pixels qui la composent ou leur densité", 2),
    ("le débit d'une connexion", "est la quantité de données transmises par unité de temps", 2),
    ("la fréquence d'un processeur", "se mesure en hertz, en nombre de cycles d'horloge par seconde", 3),
    ("la numérisation", "transforme une information analogique en suite de valeurs numériques", 2),
    ("la compression sans perte", "réduit la taille d'un fichier en permettant de retrouver exactement l'original", 3),
    ("la compression avec perte", "réduit la taille d'un fichier en abandonnant une partie de l'information", 3),
], cat="Unités et codage", src=S, fwd="Quelle description est correcte pour : {a} ?", rev="Parmi ces termes, lequel {b} ?", expl="{A} {b}.")

mcq(L1, "inf1-unit-m", [
    ("Combien de bits contient un octet ?", "8", ["4", "16", "10", "1024"], "Un octet est un groupe de 8 bits.", 1),
    ("Quelle est la valeur décimale du nombre binaire 101 ?", "5", ["3", "6", "7", "101"], "1×4 + 0×2 + 1×1 = 5.", 2),
    ("Quelle est la valeur décimale du nombre binaire 1111 ?", "15", ["16", "14", "8", "1111"], "8 + 4 + 2 + 1 = 15.", 2),
    ("Quelle est la valeur décimale du nombre binaire 10000 ?", "16", ["10", "8", "32", "10000"], "Un 1 suivi de quatre zéros vaut 2 puissance 4.", 2),
    ("Quelle est l'écriture binaire du nombre décimal 6 ?", "110", ["101", "011", "100", "111"], "6 = 4 + 2.", 2),
    ("Quelle est l'écriture binaire du nombre décimal 10 ?", "1010", ["1001", "1100", "1110", "0101"], "10 = 8 + 2.", 3),
    ("Combien de valeurs différentes peut-on représenter avec 8 bits ?", "256", ["8", "255", "128", "512"], "2 puissance 8 = 256 (de 0 à 255 pour des entiers non signés).", 3),
    ("Combien de valeurs différentes peut-on représenter avec 3 bits ?", "8", ["3", "6", "9", "16"], "2 puissance 3 = 8.", 2),
    ("Quelle est la plus grande valeur d'un entier non signé codé sur 8 bits ?", "255", ["256", "127", "128", "100"], "Les valeurs vont de 0 à 2^8 − 1.", 3),
    ("Que vaut le chiffre hexadécimal F en décimal ?", "15", ["16", "14", "6", "10"], "A=10, B=11, C=12, D=13, E=14, F=15.", 3),
    ("Que vaut le nombre hexadécimal 10 en décimal ?", "16", ["10", "1", "8", "100"], "1×16 + 0 = 16.", 3),
    ("Que vaut le nombre hexadécimal FF en décimal ?", "255", ["256", "225", "15", "240"], "15×16 + 15 = 255.", 4),
    ("Que vaut le chiffre hexadécimal A en décimal ?", "10", ["1", "11", "16", "A est interdit"], "Le chiffre A représente dix.", 2),
    ("Combien d'octets y a-t-il dans 1 kio (kibioctet) ?", "1024", ["1000", "8", "512", "1 048 576"], "Le préfixe binaire kibi vaut 2 puissance 10.", 4),
    ("Combien d'octets y a-t-il dans 1 ko (kilooctet) au sens du Système international ?", "1000", ["1024", "8", "100", "1 000 000"], "Le préfixe kilo du SI vaut 1000 ; 1024 octets s'écrivent 1 kio.", 4),
    ("Un fichier de 3 Mo (mégaoctets, au sens SI) contient combien d'octets ?", "3 000 000", ["3 000", "3 145 728", "300 000", "30 000 000"], "1 Mo = 1 000 000 octets au sens SI.", 4),
    ("Quelle unité est la plus grande ?", "Le gigaoctet", ["Le mégaoctet", "Le kilooctet", "L'octet", "Le bit"], "Go > Mo > ko > octet > bit.", 1),
    ("À quoi correspond environ un débit de 8 mégabits par seconde ?", "1 mégaoctet par seconde", ["8 mégaoctets par seconde", "64 mégaoctets par seconde", "0,1 mégaoctet par seconde", "80 mégaoctets par seconde"], "On divise par 8 pour passer des bits aux octets.", 4),
    ("Combien de temps faut-il, au minimum théorique, pour transférer 10 Mo sur une liaison de 80 mégabits par seconde (1 Mo = 8 mégabits) ?", "1 seconde", ["10 secondes", "8 secondes", "0,1 seconde", "80 secondes"], "10 Mo = 80 Mb, divisé par 80 Mb/s donne 1 s.", 5),
    ("Combien de bits faut-il au minimum pour distinguer 100 valeurs différentes ?", "7", ["6", "8", "10", "100"], "2^6 = 64 est insuffisant ; 2^7 = 128 suffit.", 5),
    ("Combien de bits faut-il au minimum pour numéroter 16 objets différents ?", "4", ["3", "5", "8", "16"], "2^4 = 16.", 4),
    ("Que vaut 1011 en binaire plus 1 en binaire ?", "1100", ["1010", "1111", "10000", "1101"], "11 + 1 = 12, soit 1100.", 4),
    ("Quelle opération sur un nombre binaire décale tous ses bits d'un rang vers la gauche, en ajoutant un 0 à droite ?", "Elle le multiplie par 2", ["Elle le divise par 2", "Elle ajoute 1", "Elle l'élève au carré", "Elle ne change pas sa valeur"], "Comme ajouter un zéro à droite multiplie par 10 en décimal.", 5),
    ("Pourquoi un nombre entier de 8 bits ne peut-il pas représenter la valeur 300 sans signe ?", "Parce que son maximum est 255", ["Parce que 300 est négatif", "Parce que 300 n'est pas pair", "Parce que 8 bits ne codent que des lettres", "Parce que 300 est un nombre premier"], "La plage va de 0 à 255.", 4),
    ("Une image de 1000 pixels de large sur 500 de haut contient combien de pixels ?", "500 000", ["1 500", "50 000", "5 000 000", "1 000 500"], "1000 × 500.", 2),
    ("Quelle est l'unité de la fréquence d'horloge d'un processeur ?", "Le hertz", ["L'octet", "Le watt", "Le pixel", "Le baud par octet"], "Un hertz est un cycle par seconde.", 2),
], cat="Unités et codage", src=S)

pairs(L1, "inf1-hist", [
    ("Alan Turing", "la machine de Turing, modèle théorique de ce qui est calculable", 2),
    ("John von Neumann", "l'architecture à programme enregistré, où instructions et données partagent la mémoire", 3),
    ("Ada Lovelace", "des notes sur la machine analytique de Babbage avec un algorithme destiné à une machine", 2),
    ("Charles Babbage", "la conception de la machine analytique, ancêtre mécanique de l'ordinateur", 2),
    ("Tim Berners-Lee", "l'invention du World Wide Web au CERN", 1),
    ("Grace Hopper", "l'un des premiers compilateurs et la promotion de langages proches de l'anglais", 3),
    ("Claude Shannon", "la théorie de l'information, fondée sur la mesure en bits", 3),
    ("Blaise Pascal", "la Pascaline, une machine à calculer mécanique du XVIIe siècle", 2),
    ("Vint Cerf et Bob Kahn", "la conception des protocoles TCP/IP", 4),
    ("Linus Torvalds", "le noyau Linux", 2),
    ("Richard Stallman", "le projet GNU et la Free Software Foundation", 3),
    ("Dennis Ritchie", "le langage C, créé aux Bell Labs", 3),
    ("Edgar Codd", "le modèle relationnel des bases de données", 4),
    ("Guido van Rossum", "le langage Python", 2),
    ("James Gosling", "le langage Java", 3),
    ("Bjarne Stroustrup", "le langage C++", 3),
    ("Gordon Moore", "l'observation selon laquelle le nombre de transistors par puce double à intervalles réguliers", 3),
    ("Donald Knuth", "la série « L'Art de la programmation informatique » (The Art of Computer Programming)", 4),
    ("Edsger Dijkstra", "un algorithme de recherche de plus court chemin dans un graphe", 4),
    ("Joseph-Marie Jacquard", "un métier à tisser commandé par cartes perforées", 4),
    ("Konrad Zuse", "le Z3, calculateur programmable construit en Allemagne dans les années 1940", 5),
    ("Bill Gates et Paul Allen", "la fondation de Microsoft", 2),
    ("Larry Page et Sergey Brin", "la fondation de Google", 2),
], region="WORLD", cat="Histoire de l'informatique", src=S, fwd="Quelle contribution est associée à {a} ?", rev="À qui associe-t-on cette contribution : {b} ?",
    expl="{a} est associé à {b}.")

table(L1, "inf1-algo", [
    ("un algorithme", "est une suite finie et ordonnée d'instructions qui résout un problème", 1),
    ("une variable", "est un emplacement nommé qui contient une valeur pouvant changer", 1),
    ("une constante", "est une valeur nommée qui ne change pas pendant l'exécution", 2),
    ("une condition (test)", "choisit les instructions à exécuter selon qu'une expression est vraie ou fausse", 1),
    ("une boucle", "répète un groupe d'instructions", 1),
    ("une fonction (ou procédure)", "regroupe des instructions sous un nom pour les réutiliser", 2),
    ("un paramètre", "est une valeur fournie à une fonction lors de son appel", 2),
    ("un tableau", "est une collection de valeurs rangées et repérées par un indice", 2),
    ("un commentaire", "est un texte du code source ignoré à l'exécution et destiné aux lecteurs", 2),
    ("un bogue (bug)", "est une erreur dans un programme qui produit un comportement imprévu", 1),
    ("le débogage", "consiste à chercher et corriger les erreurs d'un programme", 1),
    ("le pseudo-code", "décrit un algorithme dans un langage informel, sans syntaxe stricte", 2),
    ("un organigramme", "représente un algorithme par un schéma de blocs et de flèches", 2),
    ("une affectation", "range une valeur dans une variable", 2),
    ("un compilateur", "traduit tout un programme en code exécutable par la machine", 3),
    ("un interpréteur", "exécute un programme instruction par instruction sans produire d'exécutable autonome", 3),
    ("un booléen", "est une valeur qui ne peut être que vrai ou faux", 2),
    ("un identifiant", "est le nom donné à une variable, une fonction ou un objet dans un programme", 3),
], cat="Algorithmique et programmation", src=S, fwd="Quelle description est correcte pour : {a} ?", rev="Parmi ces termes, lequel {b} ?", expl="{A} {b}.")

mcq(L1, "inf1-algo-m", [
    ("Après les instructions x ← 5 puis x ← x + 2, quelle est la valeur de x ?", "7", ["5", "2", "52", "10"], "x vaut d'abord 5, puis 5 + 2.", 1),
    ("Après a ← 3, b ← 4, puis a ← b, b ← a, quelles sont les valeurs de a et b ?", "a = 4 et b = 4", ["a = 3 et b = 4", "a = 4 et b = 3", "a = 3 et b = 3", "a = 7 et b = 7"], "Après a ← b, a vaut 4 ; puis b prend la valeur de a, qui est 4 : l'échange a échoué.", 4),
    ("Quelle suite d'instructions permet d'échanger correctement les valeurs de a et de b ?", "t ← a ; a ← b ; b ← t", ["a ← b ; b ← a", "b ← a ; a ← b", "a ← a + b ; b ← a", "t ← a ; a ← t ; b ← t"], "Une variable temporaire conserve l'ancienne valeur de a.", 4),
    ("Combien de fois s'exécute le corps de la boucle « pour i de 1 à 5 » ?", "5 fois", ["4 fois", "6 fois", "1 fois", "10 fois"], "Les valeurs de i sont 1, 2, 3, 4 et 5.", 1),
    ("Quelle est la valeur de s à la fin : s ← 0 ; pour i de 1 à 4 : s ← s + i ?", "10", ["4", "6", "24", "15"], "1 + 2 + 3 + 4 = 10.", 2),
    ("Quelle est la valeur de p à la fin : p ← 1 ; pour i de 1 à 4 : p ← p × i ?", "24", ["10", "16", "4", "120"], "Produit 1×2×3×4 = 24.", 3),
    ("Quel est le reste de la division entière de 17 par 5 ?", "2", ["3", "1", "4", "12"], "17 = 5×3 + 2.", 2),
    ("Quel est le quotient de la division entière de 17 par 5 ?", "3", ["2", "4", "3,4", "12"], "5×3 = 15 ≤ 17 < 20.", 2),
    ("Quelle opération permet de savoir si un entier n est pair ?", "Tester si le reste de n divisé par 2 vaut 0", ["Tester si n est supérieur à 2", "Tester si n est divisible par 3", "Tester si n est positif", "Tester si n est un carré"], "Un entier est pair s'il est un multiple de 2.", 2),
    ("Quelle différence y a-t-il entre « = » comme affectation et « = » comme test d'égalité dans la plupart des langages ?", "Le premier range une valeur dans une variable, le second compare deux valeurs", ["Aucune, les deux comparent", "Le premier compare, le second range", "Le premier est réservé aux nombres, le second aux textes", "Le premier ouvre une boucle, le second la ferme"], "Beaucoup de langages notent la comparaison avec un symbole distinct (==, par exemple).", 3),
    ("Que fait une boucle « tant que » dont la condition reste toujours vraie ?", "Elle ne s'arrête jamais (boucle infinie)", ["Elle s'exécute une seule fois", "Elle ne s'exécute jamais", "Elle s'arrête après 10 tours", "Elle produit une erreur de syntaxe"], "Sans modification de la condition, la boucle ne se termine pas.", 2),
    ("Combien de fois s'exécute une boucle « tant que » dont la condition est fausse dès le départ ?", "Zéro fois", ["Une fois", "Deux fois", "Indéfiniment", "Autant de fois que la valeur d'une variable"], "La condition est testée avant chaque tour.", 3),
    ("Dans l'algorithme « si x > 10 alors afficher A sinon afficher B », que s'affiche-t-il pour x = 10 ?", "B", ["A", "A puis B", "Rien", "Une erreur"], "10 n'est pas strictement supérieur à 10.", 2),
    ("Quelle est la valeur de l'expression booléenne (vrai ET faux) OU vrai ?", "Vrai", ["Faux", "Indéterminée", "Erreur", "Zéro"], "Vrai ET faux vaut faux ; faux OU vrai vaut vrai.", 3),
    ("Quelle est la valeur de NON (vrai OU faux) ?", "Faux", ["Vrai", "Indéterminée", "Erreur", "Un"], "Vrai OU faux vaut vrai ; sa négation vaut faux.", 3),
    ("Dans un tableau de 10 éléments indicés à partir de 0, quel est l'indice du dernier élément ?", "9", ["10", "11", "0", "1"], "Les indices vont de 0 à 9.", 2),
    ("Dans un tableau de 10 éléments indicés à partir de 1, quel est l'indice du dernier élément ?", "10", ["9", "11", "0", "1"], "Les indices vont de 1 à 10.", 2),
    ("Quelle est la meilleure méthode pour trouver le plus grand élément d'une liste non triée de n éléments ?", "La parcourir une fois en gardant le plus grand vu jusque-là", ["Calculer la somme des éléments", "Trier d'abord par ordre décroissant puis lire le milieu", "Regarder seulement le premier élément", "Prendre au hasard dix éléments"], "Un seul passage suffit.", 3),
    ("Qu'appelle-t-on une erreur de syntaxe ?", "Une instruction qui ne respecte pas les règles d'écriture du langage", ["Un résultat faux malgré un programme bien écrit", "Une panne de l'ordinateur", "Un manque de mémoire vive", "Une coupure du réseau"], "Elle est signalée avant l'exécution ou au moment de l'analyse.", 2),
    ("Qu'appelle-t-on une erreur de logique ?", "Un programme valide qui ne fait pas ce que l'on voulait", ["Un caractère oublié dans une instruction", "Un programme qui refuse de démarrer", "Un fichier introuvable", "Un mot de passe incorrect"], "Le code s'exécute mais le résultat est faux.", 3),
    ("Pourquoi découper un programme en fonctions ?", "Pour réutiliser du code et mieux le comprendre et le tester", ["Pour qu'il s'exécute toujours plus vite", "Pour qu'il occupe plus de mémoire", "Pour supprimer le besoin de variables", "Pour éviter d'avoir à le tester"], "La décomposition améliore la lisibilité et la réutilisation.", 2),
    ("Lequel de ces types de données convient pour stocker un prénom ?", "Une chaîne de caractères", ["Un entier", "Un booléen", "Un nombre à virgule", "Un tableau de nombres"], "Un prénom est du texte.", 1),
    ("Lequel de ces types de données convient pour stocker « majeur ou non » ?", "Un booléen", ["Une chaîne de caractères", "Un tableau", "Un nombre à virgule flottante", "Une fonction"], "Deux valeurs possibles : vrai ou faux.", 2),
    ("Combien d'étapes de comparaison faut-il au plus pour trouver un nombre entre 1 et 100 en demandant à chaque fois « plus grand ou plus petit ? » (dichotomie) ?", "7", ["10", "50", "100", "5"], "2^7 = 128 ≥ 100 tandis que 2^6 = 64 < 100.", 5),
    ("Quelle est la valeur de 7 + 3 × 2 selon les règles usuelles de priorité ?", "13", ["20", "16", "10", "9"], "La multiplication est prioritaire : 7 + 6.", 1),
    ("Quelle est la valeur de (7 + 3) × 2 ?", "20", ["13", "16", "10", "12"], "Les parenthèses forcent l'addition en premier.", 1),
    ("Quelle est la valeur de la division entière 7 divisé par 2 dans un langage qui calcule des entiers ?", "3", ["3,5", "4", "2", "1"], "7 = 2×3 + 1.", 2),
], cat="Algorithmique et programmation", src=S)

table(L1, "inf1-bure", [
    ("une cellule", "est l'intersection d'une ligne et d'une colonne dans un tableur", 1),
    ("un classeur", "est le fichier d'un tableur qui regroupe une ou plusieurs feuilles", 2),
    ("une formule de tableur", "commence en général par le signe égal et calcule une valeur", 1),
    ("une référence relative", "s'adapte quand on recopie la formule vers une autre cellule", 3),
    ("une référence absolue", "reste figée sur une cellule précise, notée avec des signes dollar", 3),
    ("la fonction SOMME", "additionne les valeurs d'une plage de cellules", 1),
    ("la fonction MOYENNE", "calcule la moyenne arithmétique d'une plage de cellules", 1),
    ("un tableau croisé dynamique", "résume et regroupe de grandes quantités de données selon plusieurs critères", 3),
    ("un filtre", "n'affiche que les lignes qui répondent à un critère", 2),
    ("un style de paragraphe", "regroupe des réglages de mise en forme appliqués d'un seul coup", 2),
    ("une table des matières automatique", "se génère à partir des titres structurés par des styles", 3),
    ("un en-tête et un pied de page", "affichent un contenu répété en haut et en bas de chaque page", 1),
    ("le publipostage", "fusionne un document modèle avec une liste de destinataires", 3),
    ("un diaporama", "enchaîne des diapositives pour une présentation devant un public", 1),
    ("un graphique", "représente visuellement des données chiffrées d'un tableau", 1),
    ("le tri", "range les lignes selon l'ordre croissant ou décroissant d'une colonne", 1),
], cat="Bureautique", src=S, fwd="Quelle description est correcte pour : {a} ?", rev="Parmi ces termes, lequel {b} ?", expl="{A} {b}.")

mcq(L1, "inf1-bure-m", [
    ("Où se trouve la cellule B3 dans un tableur ?", "Dans la colonne B, à la ligne 3", ["Dans la ligne B, à la colonne 3", "À la troisième feuille", "Dans la plage A1:B3 seulement", "À la fois en colonne 3 et en ligne 3"], "La lettre repère la colonne, le nombre la ligne.", 1),
    ("Si A1 contient 4, A2 contient 6 et A3 contient 8, que renvoie =SOMME(A1:A3) ?", "18", ["12", "6", "24", "A1:A3"], "4 + 6 + 8 = 18.", 1),
    ("Avec les mêmes valeurs (4, 6, 8), que renvoie =MOYENNE(A1:A3) ?", "6", ["18", "8", "4", "7"], "18 / 3 = 6.", 2),
    ("Avec les mêmes valeurs (4, 6, 8), que renvoie =MAX(A1:A3) ?", "8", ["18", "6", "4", "3"], "Le plus grand des trois nombres.", 1),
    ("Si B1 contient la formule =A1*2 et que l'on recopie B1 vers B2, quelle formule apparaît en B2 ?", "=A2*2", ["=A1*2", "=B2*2", "=A3*2", "=A2*3"], "La référence relative suit le déplacement d'une ligne.", 3),
    ("Si B1 contient =$A$1*2 et que l'on recopie B1 vers B2, quelle formule apparaît en B2 ?", "=$A$1*2", ["=$A$2*2", "=A2*2", "=$B$2*2", "=A1*3"], "La référence absolue ne bouge pas.", 4),
    ("Si C1 contient =A1+B1 et que l'on recopie C1 vers D1 (une colonne à droite), quelle formule apparaît ?", "=B1+C1", ["=A1+B1", "=A2+B2", "=B2+C2", "=A1+C1"], "Les références relatives glissent d'une colonne.", 4),
    ("Que renvoie en général une formule =SI(A1>10;\"grand\";\"petit\") quand A1 contient 10 ?", "petit", ["grand", "10", "Erreur", "vrai"], "10 n'est pas strictement supérieur à 10.", 3),
    ("Quelle pratique rend un long document plus facile à naviguer et à mettre en forme ?", "Utiliser des styles de titres plutôt que des mises en forme manuelles", ["Mettre chaque titre en gras à la main", "Séparer les parties avec des lignes vides", "Taper des espaces pour aligner le texte", "Utiliser une police différente par page"], "Les styles permettent plan, table des matières et modification globale.", 3),
    ("À quoi sert la fonction « Enregistrer sous » ?", "À enregistrer le document sous un autre nom, un autre emplacement ou un autre format", ["À supprimer le document", "À imprimer le document", "À envoyer le document par courriel", "À fermer le logiciel sans conserver"], "Elle crée une copie à l'endroit et sous la forme choisis.", 1),
    ("Pourquoi faut-il éviter de saisir plusieurs données dans une même cellule d'un tableur (par exemple « Jean 25 ans ») ?", "Parce que cela empêche de trier, filtrer et calculer proprement", ["Parce que le tableur le refuse", "Parce que la cellule devient rouge", "Parce que le fichier ne s'enregistre plus", "Parce que cela supprime la mise en forme"], "Une donnée par cellule facilite l'exploitation.", 3),
    ("Quel type de graphique convient le mieux pour montrer une évolution dans le temps ?", "Une courbe", ["Un secteur (camembert)", "Un nuage de mots", "Un organigramme", "Un tableau à une seule cellule"], "La courbe met en évidence les variations successives.", 2),
    ("Quel type de graphique convient le mieux pour montrer la part de chaque catégorie dans un total ?", "Un secteur (camembert)", ["Une courbe", "Un nuage de points", "Un histogramme de dates", "Un diagramme de Gantt"], "Il représente des proportions d'un tout.", 2),
], cat="Bureautique", src=S)

table(L1, "inf1-mail", [
    ("le champ « Cc » d'un courriel", "met des destinataires en copie, visibles de tous", 2),
    ("le champ « Cci » ou « Bcc »", "met des destinataires en copie cachée, invisibles des autres", 2),
    ("une pièce jointe", "est un fichier envoyé avec le message", 1),
    ("une adresse électronique", "est composée d'un nom d'utilisateur, d'un arobase et d'un nom de domaine", 1),
    ("le dossier de courrier indésirable", "reçoit les messages jugés comme du spam", 2),
    ("une liste de diffusion", "relaie un même message à tous ses abonnés", 3),
    ("une signature de courriel", "est un bloc de texte ajouté automatiquement en fin de message", 2),
    ("la netiquette", "regroupe les règles de bonne conduite dans les échanges en ligne", 2),
], cat="Internet et Web", src=S, fwd="Quelle description est correcte pour : {a} ?", rev="Parmi ces termes, lequel {b} ?", expl="{A} {b}.")

table(L1, "inf1-donn", [
    ("une donnée personnelle", "est toute information se rapportant à une personne identifiée ou identifiable", 2),
    ("le consentement", "est l'accord libre et éclairé donné par la personne pour un traitement de ses données", 3),
    ("le principe de finalité", "exige que les données soient collectées pour un but précis et légitime", 3),
    ("le principe de minimisation", "veut que l'on ne collecte que les données nécessaires au but poursuivi", 3),
    ("le droit d'accès", "permet à une personne de savoir quelles données la concernant sont détenues", 3),
    ("le droit de rectification", "permet de faire corriger des données inexactes", 3),
    ("le droit à l'effacement", "permet, dans certains cas, de demander la suppression de ses données", 4),
    ("la durée de conservation limitée", "veut que les données ne soient gardées que le temps nécessaire", 3),
    ("l'anonymisation", "retire d'un jeu de données toute possibilité raisonnable d'identifier les personnes", 4),
    ("le responsable de traitement", "est l'entité qui décide des finalités et des moyens d'un traitement de données", 4),
    ("un témoin de connexion (cookie)", "est un petit fichier déposé par un site dans le navigateur", 2),
    ("une identité numérique", "est l'ensemble des traces et informations qui définissent une personne en ligne", 2),
    ("l'empreinte numérique", "regroupe les traces laissées volontairement ou non lors de l'usage d'Internet", 3),
], cat="Protection des données", src=S, fwd="Quelle description est correcte pour : {a} ?", rev="Parmi ces termes, lequel {b} ?", expl="{A} {b}.")

mcq(L1, "inf1-donn-m", [
    ("Laquelle de ces informations est une donnée personnelle ?", "L'adresse électronique d'une personne identifiée", ["La capitale d'un pays", "La formule de l'eau", "Le prix moyen du pain en général", "La date de la fête nationale"], "Elle permet de relier l'information à une personne.", 1),
    ("Une application de lampe de poche demande l'accès à vos contacts et à votre position. Quel principe est en jeu ?", "La minimisation : ces données ne sont pas nécessaires à sa finalité", ["La compression des données", "La sauvegarde en nuage", "La synchronisation d'horloge", "La numérisation analogique"], "On ne doit demander que ce qui est utile au service rendu.", 3),
    ("Pourquoi la navigation privée n'empêche-t-elle pas d'être suivi par un site visité ?", "Elle n'efface que les traces locales, pas ce que voit le site", ["Elle désactive Internet", "Elle chiffre toute la connexion", "Elle masque l'adresse IP à tous", "Elle supprime les comptes en ligne"], "Le mode privé limite les traces sur l'appareil, pas les observations à distance.", 4),
    ("Qu'est-ce qu'une information publiée sur un réseau social public ?", "Une information que d'autres peuvent copier, conserver et diffuser", ["Une information qui disparaît après 24 heures de façon certaine", "Une information protégée par le secret professionnel", "Une information visible seulement de son auteur", "Une information qui ne peut être indexée"], "Il est difficile de reprendre le contrôle de ce qui est publié.", 3),
    ("Pourquoi est-il préférable de ne pas partager de copie de pièce d'identité sans nécessité ?", "Elle peut servir à l'usurpation d'identité", ["Elle occupe trop de mémoire", "Elle est rendue illisible par l'envoi", "Elle devient la propriété du destinataire", "Elle perd sa validité légale"], "Les documents d'identité sont des données sensibles.", 2),
    ("Un site qui vous informe de sa collecte de données, vous explique pourquoi et vous laisse choisir applique surtout quel principe ?", "Transparence et consentement", ["Compression et débit", "Redondance et sauvegarde", "Antivirus et pare-feu", "Fréquence et résolution"], "La personne doit savoir et pouvoir consentir.", 3),
], cat="Protection des données", src=S)

# =====================================================================================================================
# L2
# =====================================================================================================================
S = "sup-info-l2"
FW = "Quelle description est correcte pour : {a} ?"
RV = "Parmi ces termes, lequel {b} ?"

table(L2, "inf2-arch", [
    ("le compteur ordinal (PC)", "contient l'adresse de la prochaine instruction à exécuter", 2),
    ("le registre d'instruction", "contient l'instruction en cours de décodage ou d'exécution", 3),
    ("l'unité arithmétique et logique (UAL)", "effectue les opérations arithmétiques et logiques du processeur", 2),
    ("l'unité de contrôle", "décode les instructions et commande les autres éléments du processeur", 3),
    ("le bus d'adresses", "transporte l'adresse de la case mémoire ou du périphérique visé", 3),
    ("le bus de données", "transporte les valeurs échangées entre processeur, mémoire et périphériques", 3),
    ("le bus de contrôle", "transporte des signaux de commande comme la lecture ou l'écriture", 3),
    ("le pipeline", "chevauche les étapes de plusieurs instructions pour augmenter le débit", 3),
    ("une interruption", "suspend le programme en cours pour traiter un événement, avant de le reprendre", 3),
    ("l'accès direct à la mémoire (DMA)", "permet à un périphérique de transférer des blocs sans mobiliser le processeur à chaque octet", 4),
    ("un cœur de processeur", "est une unité capable d'exécuter son propre flot d'instructions", 2),
    ("une architecture RISC", "repose sur un jeu d'instructions réduit et simple", 3),
    ("une architecture CISC", "propose un jeu d'instructions riche, avec des instructions complexes", 3),
    ("le langage machine", "est formé d'instructions codées en binaire et exécutées directement par le processeur", 2),
    ("le langage d'assemblage", "est une écriture lisible, par mnémoniques, des instructions machine", 3),
    ("le registre d'état", "contient des indicateurs sur le résultat de la dernière opération, comme zéro ou retenue", 4),
], cat="Architecture des ordinateurs", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

table(L2, "inf2-gate", [
    ("la porte ET", "donne 1 seulement si toutes ses entrées valent 1", 2),
    ("la porte OU", "donne 1 si au moins une de ses entrées vaut 1", 2),
    ("la porte NON", "inverse la valeur de son unique entrée", 1),
    ("la porte OU exclusif (XOR)", "donne 1 si ses deux entrées sont différentes", 3),
    ("la porte NON-ET (NAND)", "donne 0 seulement si toutes ses entrées valent 1", 3),
    ("la porte NON-OU (NOR)", "donne 1 seulement si toutes ses entrées valent 0", 3),
    ("un demi-additionneur", "additionne deux bits et produit une somme et une retenue", 4),
    ("une bascule (flip-flop)", "mémorise un bit tant que le circuit est alimenté", 4),
], cat="Architecture des ordinateurs", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

mcq(L2, "inf2-arch-m", [
    ("Dans quel ordre s'enchaînent les étapes de base du cycle d'exécution d'une instruction ?", "Chargement, décodage, exécution", ["Exécution, décodage, chargement", "Décodage, chargement, exécution", "Chargement, exécution, décodage", "Décodage, exécution, chargement"], "Le processeur lit l'instruction, la décode, puis l'exécute.", 2),
    ("Combien d'octets peut-on adresser avec 16 bits d'adresse, un octet par adresse ?", "65 536", ["16", "256", "1 024", "16 384"], "2^16 = 65 536.", 3),
    ("Combien d'adresses distinctes permet un bus d'adresses de 32 bits ?", "4 294 967 296", ["32 768", "65 536", "1 073 741 824", "2 147 483 647"], "2^32 adresses, soit 4 Gio si chaque adresse désigne un octet.", 4),
    ("Comment représente-t-on −1 sur 8 bits en complément à deux ?", "11111111", ["10000001", "00000001", "10000000", "01111111"], "On inverse 00000001 puis on ajoute 1.", 4),
    ("Quelle plage d'entiers un nombre signé sur 8 bits en complément à deux peut-il représenter ?", "De −128 à 127", ["De −127 à 127", "De 0 à 255", "De −255 à 255", "De −256 à 256"], "Le bit de poids fort pèse −128.", 4),
    ("Quelle est la valeur de l'octet 10000000 interprété comme un entier signé en complément à deux ?", "−128", ["−0", "−1", "128", "−127"], "C'est la plus petite valeur représentable sur 8 bits.", 5),
    ("Que vaut 00001010 ET 00000110, bit à bit ?", "00000010", ["00001110", "00001100", "00000100", "00001000"], "Seul le bit commun aux deux est conservé.", 3),
    ("Que vaut 00001010 OU 00000110, bit à bit ?", "00001110", ["00000010", "00001100", "00001000", "00000100"], "Un bit vaut 1 s'il vaut 1 dans au moins un opérande.", 3),
    ("Que vaut 00001010 XOR 00000110, bit à bit ?", "00001100", ["00001110", "00000010", "00000100", "00001000"], "Un bit vaut 1 quand les deux bits diffèrent.", 4),
    ("Pourquoi un nombre à virgule flottante comme 0,1 est-il en général représenté de façon approchée dans un ordinateur ?", "Parce qu'il n'a pas d'écriture binaire finie", ["Parce que 0,1 est négatif", "Parce que le processeur ne connaît pas la virgule", "Parce que 0,1 dépasse la capacité de la mémoire", "Parce que la RAM arrondit au hasard"], "Comme 1/3 en décimal, 0,1 est périodique en base 2.", 4),
    ("Que signifie qu'un système est « petit-boutiste » (little-endian) ?", "L'octet de poids faible d'un nombre est stocké à l'adresse la plus basse", ["L'octet de poids fort est stocké à l'adresse la plus basse", "Les nombres sont stockés en base 10", "Les nombres sont stockés sur un seul bit", "Les adresses sont numérotées à l'envers"], "Il s'agit de l'ordre des octets d'un mot en mémoire.", 5),
    ("Quel avantage apporte une mémoire cache entre processeur et mémoire vive ?", "Elle réduit le temps moyen d'accès grâce à la localité des accès", ["Elle augmente la capacité de stockage durable", "Elle chiffre les données en mémoire", "Elle remplace l'unité de contrôle", "Elle rend la mémoire non volatile"], "Les données récemment utilisées ont de fortes chances d'être réutilisées.", 3),
    ("Qu'appelle-t-on localité spatiale ?", "La tendance à accéder à des adresses proches de celles déjà utilisées", ["La tendance à ne jamais réutiliser une donnée", "L'emplacement physique du processeur", "La distance entre deux ordinateurs d'un réseau", "La taille d'une adresse"], "Elle justifie le chargement de blocs entiers dans le cache.", 5),
    ("Un processeur à 4 cœurs exécute-t-il forcément un programme quelconque quatre fois plus vite ?", "Non, seul le travail parallélisable en profite", ["Oui, toujours", "Non, il va toujours deux fois plus vite", "Oui, mais seulement en lecture de disque", "Non, il va toujours plus lentement"], "Les parties séquentielles ne se répartissent pas sur plusieurs cœurs.", 4),
    ("Dans l'architecture de von Neumann, où sont stockés les programmes ?", "Dans la même mémoire que les données", ["Dans une mémoire réservée aux seuls périphériques", "Uniquement dans le processeur", "Uniquement sur le disque", "Dans une mémoire distincte et inaccessible"], "Instructions et données partagent la mémoire.", 3),
    ("Qu'est-ce qui distingue le mode noyau du mode utilisateur d'un processeur ?", "Le mode noyau autorise des instructions privilégiées réservées au système", ["Le mode noyau est plus lent", "Le mode utilisateur est réservé aux administrateurs", "Le mode noyau ne peut exécuter que des applications", "Il n'existe qu'un seul mode"], "Cette séparation protège le système contre les programmes.", 4),
    ("À quoi sert principalement une instruction de saut conditionnel ?", "À modifier l'ordre d'exécution selon le résultat d'un test", ["À arrêter l'ordinateur", "À copier un fichier", "À agrandir la mémoire", "À chiffrer un message"], "Elle implante les tests et les boucles au niveau machine.", 3),
], cat="Architecture des ordinateurs", src=S)

classify(L2, "inf2-osi", {
    "la couche application": ["HTTP", "SMTP", "DNS", "FTP", "SSH"],
    "la couche transport": ["TCP", "UDP"],
    "la couche réseau": ["IP", "ICMP", "un routeur", "une adresse IP"],
    "la couche liaison de données": ["Ethernet", "PPP", "une adresse MAC", "un commutateur (switch)"],
    "la couche physique": ["la fibre optique", "le câble à paires torsadées", "le câble coaxial", "les ondes radio d'un lien sans fil"],
}, cat="Réseaux", src=S, fwd="À quelle couche du modèle OSI rattache-t-on {item} ?", rev="Lequel de ces éléments se rattache à {group} du modèle OSI ?", expl="{item} relève de {group}.", diff=3,
    diffs={"ICMP": 4, "PPP": 4, "un commutateur (switch)": 4, "une adresse MAC": 4, "UDP": 3, "SSH": 4})

pairs(L2, "inf2-osi-n", [
    ("la couche 1 du modèle OSI", "La couche physique", 3),
    ("la couche 2 du modèle OSI", "La couche liaison de données", 3),
    ("la couche 3 du modèle OSI", "La couche réseau", 3),
    ("la couche 4 du modèle OSI", "La couche transport", 3),
    ("la couche 5 du modèle OSI", "La couche session", 4),
    ("la couche 6 du modèle OSI", "La couche présentation", 4),
    ("la couche 7 du modèle OSI", "La couche application", 3),
], region="WORLD", cat="Réseaux", src=S, fwd="Comment s'appelle {a} ?", rev="Quel numéro porte, dans le modèle OSI, cette couche : {b} ?", expl="{a} est {b}.")

table(L2, "inf2-proc", [
    ("un processus", "est un programme en cours d'exécution, avec son espace mémoire et ses ressources", 2),
    ("un thread (fil d'exécution)", "est un flot d'exécution qui partage l'espace mémoire de son processus", 3),
    ("l'ordonnanceur", "désigne le processus prêt qui reçoit le processeur", 3),
    ("le changement de contexte", "sauvegarde l'état du processus courant et charge celui d'un autre", 4),
    ("un appel système", "est une demande faite au noyau pour accéder à un service protégé", 3),
    ("le noyau (kernel)", "est le cœur du système, qui s'exécute en mode privilégié et gère les ressources", 2),
    ("un interblocage (deadlock)", "bloque des processus qui s'attendent mutuellement sans qu'aucun puisse avancer", 3),
    ("une section critique", "est un code d'accès à une ressource partagée, à exécuter par un seul fil à la fois", 4),
    ("un mutex", "est un verrou qui n'a qu'un seul détenteur à la fois", 4),
    ("un sémaphore", "est un compteur protégé utilisé pour synchroniser des processus", 4),
    ("une situation de compétition (race condition)", "donne un résultat qui dépend de l'ordre imprévisible d'accès concurrents", 4),
    ("la famine (starvation)", "laisse un processus prêt sans jamais obtenir la ressource qu'il attend", 4),
    ("un processus à l'état « prêt »", "n'attend plus que l'ordonnanceur lui donne le processeur", 3),
    ("un processus à l'état « bloqué »", "attend un événement extérieur, comme la fin d'une entrée-sortie", 3),
    ("un processus à l'état « en exécution »", "utilise actuellement le processeur", 2),
], cat="Systèmes d'exploitation", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

table(L2, "inf2-sched", [
    ("l'ordonnancement FCFS (premier arrivé, premier servi)", "sert les processus dans l'ordre d'arrivée", 2),
    ("l'ordonnancement du plus court d'abord (SJF)", "choisit le processus dont la durée d'exécution estimée est la plus courte", 3),
    ("l'ordonnancement circulaire (Round Robin)", "donne à chaque processus une tranche de temps à tour de rôle", 3),
    ("l'ordonnancement par priorités", "choisit le processus dont la priorité est la plus élevée", 3),
    ("un ordonnancement préemptif", "peut retirer le processeur à un processus avant qu'il ait fini", 4),
    ("un ordonnancement non préemptif", "laisse le processus s'exécuter jusqu'à ce qu'il se bloque ou se termine", 4),
    ("le quantum de temps", "est la durée maximale d'une tranche accordée à un processus", 3),
    ("le vieillissement (aging)", "relève peu à peu la priorité des processus qui attendent longtemps pour éviter la famine", 5),
    ("l'effet de convoi", "survient quand un long processus fait attendre derrière lui plusieurs processus courts", 5),
], cat="Systèmes d'exploitation", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

mcq(L2, "inf2-sched-m", [
    ("Trois processus P1, P2, P3 arrivent ensemble dans cet ordre avec des durées de 24, 3 et 3 unités. Quel est le temps d'attente moyen en FCFS ?", "17", ["3", "10", "9", "27"], "Attentes : 0, 24 et 27, soit 51 / 3 = 17.", 4),
    ("Mêmes processus (24, 3 et 3 unités), arrivés ensemble. Quel est le temps d'attente moyen avec le plus court d'abord (SJF) ?", "3", ["17", "10", "6", "9"], "Ordre 3, 3, 24 : attentes 0, 3 et 6, soit 9 / 3 = 3.", 5),
    ("Trois processus de durées 5, 2 et 1 arrivent ensemble. Quel est le temps d'attente moyen en FCFS dans l'ordre 5, 2, 1 ?", "4", ["3", "2", "5", "8"], "Attentes 0, 5, 7 : 12 / 3 = 4.", 4),
    ("Deux processus de durées 4 et 3 arrivent ensemble, quantum de 2, Round Robin, P1 passe d'abord. Quand P2 se termine-t-il ?", "À l'instant 7", ["À l'instant 3", "À l'instant 5", "À l'instant 6", "À l'instant 4"], "P1 0-2, P2 2-4, P1 4-6 (fini), P2 6-7.", 5),
    ("Un seul processeur, quantum de 10 ms, trois processus toujours prêts. Combien de temps attend un processus, au plus, entre deux tranches consécutives en Round Robin (changement de contexte négligé) ?", "20 ms", ["10 ms", "30 ms", "0 ms", "40 ms"], "Les deux autres processus consomment chacun 10 ms.", 5),
    ("Quelles sont les quatre conditions nécessaires à l'apparition d'un interblocage ?", "Exclusion mutuelle, détention et attente, absence de préemption, attente circulaire", ["Priorité, quantum, famine, pagination", "Pagination, segmentation, cache, bus", "Lecture, écriture, exécution, suppression", "Chargement, décodage, exécution, écriture"], "Si l'une manque, l'interblocage ne peut pas survenir.", 4),
    ("Comment peut-on empêcher l'attente circulaire entre ressources ?", "En imposant un ordre global dans lequel les ressources doivent être demandées", ["En augmentant la mémoire vive", "En réduisant le quantum", "En ajoutant un cœur au processeur", "En supprimant les fichiers temporaires"], "Un ordre total sur les ressources casse les cycles d'attente.", 5),
    ("Deux threads incrémentent un même compteur partagé sans verrou. Quel risque court-on ?", "Perdre des incréments à cause d'une situation de compétition", ["Que le compteur devienne négatif à coup sûr", "Que le processeur surchauffe", "Que le compteur soit chiffré", "Que le programme change de langage"], "La séquence lire, ajouter, écrire n'est pas atomique.", 4),
    ("Quelle est la principale différence entre un processus et un thread ?", "Les threads d'un processus partagent sa mémoire ; deux processus ont des espaces séparés", ["Un thread ne peut exister que sur disque", "Un processus n'utilise jamais de mémoire", "Un thread est toujours plus gros qu'un processus", "Les processus partagent tous le même espace mémoire"], "Les threads sont plus légers mais moins isolés.", 3),
    ("Que se passe-t-il quand un programme provoque une erreur de protection mémoire (accès à une adresse interdite) ?", "Le système interrompt le programme fautif, sans arrêter les autres", ["Tout l'ordinateur redémarre", "Le programme obtient les droits administrateur", "L'adresse est ignorée et le programme continue sans effet", "Le disque est reformaté"], "L'isolation des processus est assurée par le matériel et le noyau.", 3),
], cat="Systèmes d'exploitation", src=S)

table(L2, "inf2-mem", [
    ("la mémoire virtuelle", "donne à chaque processus l'illusion d'un grand espace d'adressage privé", 3),
    ("la pagination", "découpe la mémoire en blocs de taille fixe", 3),
    ("une page", "est un bloc de taille fixe de l'espace d'adressage virtuel", 3),
    ("un cadre de page", "est un bloc de taille fixe de la mémoire physique", 4),
    ("la table des pages", "associe les pages virtuelles d'un processus aux cadres de la mémoire physique", 4),
    ("un défaut de page", "survient quand la page demandée n'est pas présente en mémoire physique", 4),
    ("le TLB", "est un petit cache des traductions d'adresses récemment utilisées", 5),
    ("l'espace d'échange (swap)", "est une zone du disque où l'on déplace des pages sorties de la RAM", 4),
    ("la segmentation", "découpe la mémoire en segments de tailles variables ayant un sens logique", 4),
    ("la fragmentation externe", "laisse de la mémoire libre éparpillée en petits trous inutilisables", 5),
    ("la fragmentation interne", "gaspille la place d'un bloc alloué plus grand que le besoin", 5),
    ("l'écroulement (thrashing)", "fait passer l'essentiel du temps à échanger des pages plutôt qu'à travailler", 5),
    ("l'algorithme de remplacement LRU", "évince la page non utilisée depuis le plus longtemps", 4),
    ("l'algorithme de remplacement FIFO", "évince la page chargée depuis le plus longtemps", 4),
], cat="Systèmes d'exploitation", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

mcq(L2, "inf2-mem-m", [
    ("Avec des pages de 4 096 octets, l'adresse virtuelle 8 200 se trouve dans quelle page (numérotée à partir de 0) ?", "La page 2", ["La page 1", "La page 3", "La page 0", "La page 8"], "8 200 = 2 × 4 096 + 8.", 4),
    ("Avec des pages de 4 096 octets, quel est le déplacement (offset) de l'adresse virtuelle 8 200 dans sa page ?", "8", ["200", "4 096", "0", "2"], "Reste de la division de 8 200 par 4 096.", 4),
    ("Avec des adresses virtuelles de 32 bits et des pages de 4 Kio (12 bits de déplacement), combien de bits servent au numéro de page ?", "20", ["12", "32", "16", "10"], "32 − 12 = 20 bits, soit 2^20 pages.", 5),
    ("Quelle est la taille de page, en octets, si le déplacement est codé sur 10 bits ?", "1 024", ["10", "100", "512", "2 048"], "2^10 = 1 024.", 4),
    ("Que provoque en général un défaut de page ?", "Un chargement de la page depuis le disque, puis la reprise de l'instruction", ["Un arrêt définitif du système", "La suppression du fichier concerné", "Un redémarrage du processeur", "Une mise à jour automatique"], "Le noyau amène la page en mémoire physique.", 4),
    ("Dans la suite de références de pages 1, 2, 3, 1, 4 avec 3 cadres et le remplacement FIFO, quelle page est évincée lors de l'arrivée de la page 4 ?", "La page 1", ["La page 2", "La page 3", "Aucune", "La page 4"], "Cadres : 1, 2, 3 ; la page 1 est la plus ancienne chargée, donc évincée malgré sa réutilisation récente.", 5),
    ("Même suite 1, 2, 3, 1, 4 avec 3 cadres et le remplacement LRU : quelle page est évincée à l'arrivée de la page 4 ?", "La page 2", ["La page 1", "La page 3", "Aucune", "La page 4"], "La page 2 est la moins récemment utilisée (la page 1 vient d'être réutilisée).", 5),
    ("Pourquoi la mémoire virtuelle améliore-t-elle l'isolation entre processus ?", "Chaque processus a ses propres adresses, traduites vers des zones physiques distinctes", ["Parce qu'elle chiffre tout le disque", "Parce qu'elle interdit le multitâche", "Parce qu'elle donne la même adresse physique à tous", "Parce qu'elle supprime le noyau"], "Les tables de pages empêchent d'accéder à la mémoire d'un autre processus.", 4),
], cat="Systèmes d'exploitation", src=S)

table(L2, "inf2-fs", [
    ("un système de fichiers", "organise la façon dont des données nommées sont stockées sur un support", 2),
    ("un inode", "est une structure de type Unix qui contient les métadonnées d'un fichier, hors son nom", 5),
    ("un chemin absolu", "part de la racine de l'arborescence", 2),
    ("un chemin relatif", "part du répertoire courant", 2),
    ("un lien symbolique", "est un fichier particulier qui désigne un autre chemin", 4),
    ("la journalisation", "note les opérations avant de les appliquer pour limiter la corruption après une panne", 5),
    ("le montage", "rattache un système de fichiers à l'arborescence visible", 4),
    ("une partition", "est une portion d'un disque traitée comme un volume distinct", 2),
    ("le formatage", "prépare un volume à recevoir un système de fichiers", 2),
    ("les droits d'accès", "précisent qui peut lire, écrire ou exécuter un fichier", 2),
    ("la racine", "est le répertoire situé au sommet de l'arborescence", 2),
    ("les métadonnées d'un fichier", "décrivent le fichier, comme sa taille, ses dates ou ses droits", 3),
], cat="Systèmes d'exploitation", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

mcq(L2, "inf2-fs-m", [
    ("Sous Unix, quels droits représente le code octal 755 pour un fichier ?", "rwxr-xr-x", ["rw-r--r--", "rwxrwxrwx", "r-xr-xr-x", "rwx------"], "7 = rwx, 5 = r-x pour le groupe et pour les autres.", 4),
    ("Sous Unix, quels droits représente le code octal 644 ?", "rw-r--r--", ["rwxr-xr-x", "rw-rw-rw-", "r--r--r--", "rwx------"], "6 = rw-, 4 = r--.", 4),
    ("Sous Unix, que signifie le code octal 600 pour un fichier ?", "Lecture et écriture pour le propriétaire seul", ["Lecture seule pour tous", "Exécution pour tous", "Écriture pour tous", "Aucun droit pour personne"], "6 = rw- pour le propriétaire, 0 pour les autres.", 4),
    ("Que fait supprimer un fichier sur la plupart des systèmes de fichiers ?", "Libère son entrée et son espace, sans forcément effacer immédiatement son contenu", ["Détruit physiquement le disque", "Efface toujours chaque octet au hasard", "Déplace le fichier sur le Web", "Chiffre le contenu"], "Les données peuvent rester récupérables tant que l'espace n'est pas réutilisé.", 4),
    ("Quel répertoire désigne le nom formé de deux points consécutifs dans un chemin d'accès usuel ?", "Le répertoire parent", ["Le répertoire racine", "Le répertoire courant", "Le dernier fichier ouvert", "Le dossier des téléchargements"], "Le point simple désigne le répertoire courant.", 3),
    ("Quelle est la différence entre un fichier et son contenu ?", "Le fichier regroupe un nom, des métadonnées et des données", ["Aucune, ce sont deux mots pour un octet", "Le contenu est toujours un programme", "Le fichier n'existe qu'en mémoire", "Le contenu se réduit au nom"], "Le système distingue nom, métadonnées et données.", 3),
], cat="Systèmes d'exploitation", src=S)

table(L2, "inf2-proto", [
    ("le protocole HTTP", "sert à échanger des pages et ressources web entre un navigateur et un serveur", 1),
    ("le protocole HTTPS", "est HTTP protégé par un chiffrement de la communication", 2),
    ("le protocole DNS", "traduit un nom de domaine en adresse IP", 2),
    ("le protocole DHCP", "attribue automatiquement une configuration IP aux machines d'un réseau", 3),
    ("le protocole SMTP", "achemine les courriers électroniques sortants vers les serveurs", 3),
    ("le protocole IMAP", "permet de consulter son courrier en le laissant sur le serveur", 4),
    ("le protocole POP3", "rapatrie le courrier du serveur vers la machine de l'utilisateur", 4),
    ("le protocole FTP", "transfère des fichiers entre un client et un serveur", 2),
    ("le protocole SSH", "ouvre une session distante sécurisée, avec chiffrement", 3),
    ("le protocole Telnet", "ouvre une session distante dont les échanges circulent en clair", 4),
    ("le protocole ARP", "retrouve l'adresse MAC associée à une adresse IP sur le réseau local", 4),
    ("le protocole ICMP", "transporte des messages de contrôle et d'erreur, comme ceux de la commande ping", 4),
    ("le protocole NTP", "synchronise les horloges des machines", 4),
    ("le protocole TCP", "assure un transport fiable, en mode connecté, avec reprise des pertes", 3),
    ("le protocole UDP", "assure un transport sans connexion et sans garantie de livraison", 3),
    ("la traduction d'adresses (NAT)", "remplace des adresses privées par une adresse publique au passage d'un routeur", 4),
], cat="Réseaux", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

pairs(L2, "inf2-port", [
    ("HTTP", "80", 2), ("HTTPS", "443", 2), ("SSH", "22", 3), ("FTP (canal de commande)", "21", 4),
    ("SMTP (échange entre serveurs)", "25", 4), ("DNS", "53", 3), ("POP3", "110", 5), ("IMAP", "143", 5), ("Telnet", "23", 4),
], region="WORLD", cat="Réseaux", src=S, fwd="Quel numéro de port est attribué par défaut à : {a} ?", rev="Quel protocole utilise par défaut le port {b} ?", expl="{a} utilise par défaut le port {b}.")

pairs(L2, "inf2-dnsrec", [
    ("un enregistrement A", "Il associe un nom à une adresse IPv4", 4),
    ("un enregistrement AAAA", "Il associe un nom à une adresse IPv6", 4),
    ("un enregistrement MX", "Il désigne les serveurs qui reçoivent le courrier d'un domaine", 4),
    ("un enregistrement CNAME", "Il fait d'un nom l'alias d'un autre nom", 4),
    ("un enregistrement NS", "Il désigne les serveurs de noms qui font autorité pour une zone", 5),
    ("un enregistrement PTR", "Il associe une adresse IP à un nom (résolution inverse)", 5),
], region="WORLD", cat="Réseaux", src=S, fwd="Quel est le rôle de {a} dans le DNS ?", rev="De quel enregistrement DNS s'agit-il : {b} ?", expl="{a} : {b}.")

pairs(L2, "inf2-http", [
    ("200", "La requête a réussi", 2),
    ("301", "La ressource a été déplacée définitivement", 4),
    ("401", "Une authentification est requise", 4),
    ("403", "L'accès est refusé malgré une requête comprise", 4),
    ("404", "La ressource demandée est introuvable", 1),
    ("500", "Le serveur a rencontré une erreur interne", 3),
    ("503", "Le service est temporairement indisponible", 4),
], region="WORLD", cat="Réseaux", src=S, fwd="Que signifie le code de statut HTTP {a} ?", rev="Quel code de statut HTTP signifie : {b} ?", expl="Code {a} : {b}.")

pairs(L2, "inf2-meth", [
    ("GET", "Lire une ressource sans la modifier", 2),
    ("POST", "Envoyer des données à traiter par le serveur", 3),
    ("PUT", "Remplacer ou créer une ressource à une adresse donnée", 4),
    ("DELETE", "Supprimer une ressource", 3),
    ("HEAD", "Obtenir seulement les en-têtes de la réponse", 5),
], region="WORLD", cat="Réseaux", src=S, fwd="À quoi sert surtout la méthode HTTP {a} ?", rev="Quelle méthode HTTP sert à : {b} ?", expl="{a} : {b}.")

table(L2, "inf2-ip", [
    ("une adresse IPv4", "est codée sur 32 bits et souvent écrite en quatre nombres séparés par des points", 2),
    ("une adresse IPv6", "est codée sur 128 bits et écrite en groupes hexadécimaux", 3),
    ("une adresse MAC", "identifie une interface réseau sur le lien local", 3),
    ("un masque de sous-réseau", "sépare dans une adresse IP la partie réseau de la partie machine", 3),
    ("la passerelle par défaut", "est le routeur vers lequel partent les paquets destinés à l'extérieur du réseau local", 3),
    ("l'adresse de diffusion (broadcast)", "vise toutes les machines d'un même réseau", 4),
    ("l'adresse de bouclage 127.0.0.1", "désigne la machine locale elle-même", 3),
    ("un port", "est un numéro qui identifie un service ou une application sur une machine", 2),
    ("un socket", "est un point de communication défini notamment par une adresse IP et un port", 4),
    ("un paquet", "est l'unité de données de la couche réseau", 3),
    ("une trame", "est l'unité de données de la couche liaison", 4),
    ("un routeur", "relie des réseaux différents et décide du chemin des paquets", 2),
    ("un commutateur (switch)", "relie des machines d'un même réseau local en s'appuyant sur les adresses MAC", 3),
    ("un point d'accès Wi-Fi", "relie des appareils sans fil à un réseau filaire", 2),
    ("le TTL", "limite la durée de vie d'un paquet en décomptant les routeurs traversés", 4),
    ("la latence", "est le délai que met une donnée pour aller d'un point à un autre", 3),
    ("la bande passante", "est la capacité maximale de transmission d'une liaison", 3),
], cat="Réseaux", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

table(L2, "inf2-lan", [
    ("un réseau local (LAN)", "couvre une zone restreinte comme une maison, une salle ou un bâtiment", 1),
    ("un réseau étendu (WAN)", "relie des sites géographiquement éloignés", 2),
    ("une topologie en étoile", "relie chaque machine à un équipement central", 3),
    ("une topologie en bus", "relie toutes les machines à un même câble partagé", 3),
    ("une topologie en anneau", "relie chaque machine à deux voisines pour former une boucle", 4),
    ("un réseau privé virtuel (VPN)", "relie des sites ou des machines à travers Internet par un tunnel chiffré", 3),
    ("une zone démilitarisée (DMZ)", "isole des serveurs exposés à Internet du réseau interne", 4),
    ("un réseau poste à poste (pair à pair)", "fait jouer à chaque machine à la fois le rôle de client et de serveur", 3),
    ("le modèle client-serveur", "répartit les rôles entre machines qui demandent et machines qui fournissent des services", 2),
    ("un VLAN", "sépare logiquement des machines d'un même commutateur en réseaux distincts", 5),
], cat="Réseaux", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

mcq(L2, "inf2-ip-m", [
    ("Combien de machines peut-on adresser, au plus, dans un réseau IPv4 de masque /24 ?", "254", ["256", "255", "24", "252"], "256 adresses moins l'adresse de réseau et celle de diffusion.", 3),
    ("Combien d'hôtes utilisables permet un réseau IPv4 de masque /26 ?", "62", ["64", "60", "26", "126"], "2^6 = 64 adresses, moins 2.", 4),
    ("Combien d'hôtes utilisables permet un réseau IPv4 de masque /30 ?", "2", ["4", "0", "1", "30"], "4 adresses moins 2.", 4),
    ("Combien d'hôtes utilisables permet un réseau IPv4 de masque /28 ?", "14", ["16", "28", "12", "30"], "16 adresses moins 2.", 4),
    ("Combien d'adresses (au total) contient un réseau IPv4 de masque /16 ?", "65 536", ["65 534", "16 384", "256", "4 096"], "2^16 adresses.", 4),
    ("Quel préfixe équivaut au masque 255.255.255.192 ?", "/26", ["/24", "/25", "/27", "/28"], "Deux bits de plus que /24 dans le dernier octet (11000000).", 4),
    ("Quel préfixe équivaut au masque 255.255.255.240 ?", "/28", ["/26", "/27", "/29", "/30"], "Le dernier octet vaut 11110000.", 4),
    ("Quel préfixe équivaut au masque 255.255.0.0 ?", "/16", ["/8", "/24", "/32", "/12"], "Deux octets à 255.", 2),
    ("Quelle est l'adresse du réseau auquel appartient 192.168.1.130/25 ?", "192.168.1.128", ["192.168.1.0", "192.168.1.130", "192.168.1.192", "192.168.1.255"], "Avec /25, le dernier octet se coupe en blocs de 128 : 130 est dans 128-255.", 5),
    ("Quelle est l'adresse de diffusion du réseau de 10.1.2.77/26 ?", "10.1.2.127", ["10.1.2.64", "10.1.2.255", "10.1.2.76", "10.1.2.63"], "Blocs de 64 : le réseau couvre 64 à 127.", 5),
    ("Quelle est l'adresse du réseau de 172.16.5.200/20 ?", "172.16.0.0", ["172.16.5.0", "172.16.5.192", "172.16.4.0", "172.16.16.0"], "/20 : 4 bits utilisés dans le troisième octet ; 5 appartient au bloc 0 à 15.", 5),
    ("Les adresses 192.168.1.10/24 et 192.168.1.200/24 sont-elles dans le même réseau ?", "Oui, les trois premiers octets sont identiques", ["Non, car leurs derniers octets diffèrent", "Non, car 200 est supérieur à 100", "Oui, mais seulement si elles ont le même port", "Non, car la première est paire"], "Avec /24, seul le dernier octet identifie la machine.", 3),
    ("Parmi ces adresses, laquelle est une adresse IPv4 privée ?", "172.20.1.1", ["172.32.0.1", "8.8.8.8", "193.168.0.1", "100.200.1.1"], "La plage privée 172.16.0.0/12 va de 172.16 à 172.31.", 4),
    ("Parmi ces adresses, laquelle est une adresse IPv4 privée ?", "192.168.0.15", ["192.169.0.15", "11.0.0.1", "172.15.0.1", "200.168.0.15"], "192.168.0.0/16 est réservée aux usages privés.", 3),
    ("Quel est le plus long préfixe qui suffit à contenir 100 machines ?", "/25", ["/24", "/26", "/27", "/30"], "Un /26 offre 62 hôtes, un /25 en offre 126.", 5),
    ("Quelle longueur de masque suffit pour 30 hôtes, avec le moins d'adresses gaspillées ?", "/27", ["/26", "/28", "/25", "/29"], "/27 donne 30 hôtes utilisables ; /28 n'en donne que 14.", 5),
    ("Un routeur a trois routes : 10.0.0.0/8, 10.1.0.0/16 et 0.0.0.0/0. Quelle route sélectionne-t-il pour 10.1.2.3 ?", "10.1.0.0/16", ["10.0.0.0/8", "0.0.0.0/0", "Aucune", "Les trois à la fois"], "On choisit la route au préfixe le plus long qui correspond.", 5),
    ("Dans quel ordre se déroule l'établissement d'une connexion TCP ?", "SYN, SYN-ACK, ACK", ["ACK, SYN, SYN-ACK", "SYN, ACK, SYN-ACK", "SYN-ACK, SYN, ACK", "FIN, ACK, SYN"], "C'est la poignée de main en trois temps.", 4),
    ("Pourquoi le streaming vidéo ou la voix sur IP utilisent-ils parfois UDP ?", "Parce qu'une latence faible compte plus que la retransmission de chaque paquet perdu", ["Parce qu'UDP garantit la livraison", "Parce qu'UDP chiffre toujours les données", "Parce qu'UDP n'utilise pas de ports", "Parce qu'UDP fonctionne sans réseau"], "Retransmettre un paquet périmé n'a souvent aucun intérêt.", 4),
    ("Quel équipement sépare les domaines de diffusion entre réseaux IP distincts ?", "Un routeur", ["Un concentrateur (hub)", "Un câble coaxial", "Un répéteur", "Un adaptateur USB"], "Le routeur ne transmet pas la diffusion d'un réseau à l'autre.", 4),
    ("Quelle commande ou quel outil teste l'accessibilité d'une machine en envoyant des messages ICMP d'écho ?", "ping", ["ftp", "chmod", "ssh", "cd"], "Ping envoie une requête d'écho et attend la réponse.", 2),
    ("Qu'est-ce qui permet à plusieurs appareils d'un foyer de partager une seule adresse IP publique ?", "La traduction d'adresses (NAT) du routeur", ["Le pare-feu seul", "Le serveur de messagerie", "Le format du câble", "L'adresse MAC de l'ordinateur"], "Le routeur remplace les adresses privées par l'adresse publique.", 4),
    ("Quelle différence y a-t-il entre l'adresse IP et l'adresse MAC ?", "L'IP est logique et dépend du réseau, la MAC est liée à l'interface réseau", ["L'IP est toujours fixe, la MAC change à chaque connexion", "Elles désignent exactement la même chose", "L'IP est de couche physique, la MAC de couche application", "La MAC sert uniquement aux sites web"], "L'IP s'attribue ; la MAC est en principe fournie avec le matériel.", 4),
    ("Quelle est la taille d'une adresse MAC ?", "48 bits", ["32 bits", "64 bits", "128 bits", "16 bits"], "Elle s'écrit en six octets.", 4),
    ("Quel principe distingue le Wi-Fi 2,4 GHz du 5 GHz de façon générale ?", "Le 2,4 GHz porte plus loin à travers les obstacles, le 5 GHz offre en général plus de débit", ["Le 5 GHz porte toujours plus loin", "Le 2,4 GHz est sans fil et le 5 GHz filaire", "Le 2,4 GHz est réservé aux téléphones", "Il n'y a aucune différence"], "Les basses fréquences pénètrent mieux ; les hautes ont plus de bande disponible.", 4),
    ("Pourquoi WEP est-il aujourd'hui déconseillé pour sécuriser un réseau Wi-Fi ?", "Il est cassable facilement ; on préfère WPA2 ou WPA3", ["Il est trop récent", "Il n'est compatible qu'avec les téléphones", "Il impose un câble", "Il désactive le routage"], "Le protocole WEP a des faiblesses cryptographiques connues.", 4),
    ("Qu'est-ce qu'un SSID ?", "Le nom d'un réseau Wi-Fi", ["Le mot de passe du routeur", "L'adresse MAC du routeur", "Le débit maximal du réseau", "Le numéro de port du navigateur"], "Il permet de reconnaître le réseau dans la liste.", 2),
    ("Le DNS est-il nécessaire pour qu'un navigateur puisse atteindre un serveur ?", "Pas si l'on connaît l'adresse IP, mais il évite d'avoir à la retenir", ["Oui, aucun serveur n'est joignable sans lui", "Non, car les noms sont inutiles", "Oui, car l'IP n'existe pas sans DNS", "Non, car le navigateur calcule l'IP à partir du nom"], "On peut saisir directement l'adresse IP.", 4),
], cat="Réseaux", src=S)

table(L2, "inf2-bd", [
    ("une relation (table)", "est un ensemble de lignes de même structure, décrit par ses colonnes", 2),
    ("un tuple (ligne)", "est un enregistrement qui regroupe une valeur pour chaque attribut", 2),
    ("un attribut (colonne)", "est une propriété nommée dont chaque ligne porte une valeur", 2),
    ("la clé primaire", "identifie de façon unique chaque ligne d'une table et ne peut être nulle", 2),
    ("la clé étrangère", "est une colonne qui référence la clé primaire d'une autre table", 3),
    ("une clé candidate", "est un ensemble minimal d'attributs qui identifie chaque ligne", 4),
    ("l'intégrité référentielle", "interdit qu'une clé étrangère désigne une ligne qui n'existe pas", 4),
    ("un index", "est une structure auxiliaire qui accélère la recherche sur certaines colonnes", 3),
    ("une vue", "est une requête enregistrée qui se consulte comme une table virtuelle", 4),
    ("la valeur NULL", "représente l'absence de valeur connue, différente de zéro ou du texte vide", 4),
    ("un système de gestion de base de données (SGBD)", "est le logiciel qui stocke, protège et interroge les données", 2),
    ("le schéma d'une base", "décrit la structure des tables, des colonnes et des contraintes", 3),
    ("la redondance des données", "est la répétition d'une même information en plusieurs endroits", 3),
    ("l'association plusieurs-à-plusieurs", "se représente par une table de liaison qui contient deux clés étrangères", 5),
    ("une dépendance fonctionnelle", "indique que la valeur d'un attribut détermine celle d'un autre", 5),
], cat="Bases de données", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

table(L2, "inf2-norm", [
    ("la première forme normale (1FN)", "exige des valeurs atomiques dans chaque cellule", 4),
    ("la deuxième forme normale (2FN)", "exclut les dépendances d'un attribut envers une partie seulement de la clé", 5),
    ("la troisième forme normale (3FN)", "exclut les dépendances entre attributs qui ne font pas partie de la clé", 5),
    ("la normalisation", "décompose les tables pour réduire la redondance et les anomalies de mise à jour", 3),
    ("l'anomalie de mise à jour", "apparaît quand une information répétée n'est modifiée qu'à certains endroits", 5),
    ("la dénormalisation", "réintroduit volontairement de la redondance pour accélérer certaines lectures", 5),
], cat="Bases de données", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

table(L2, "inf2-sql", [
    ("la clause SELECT", "choisit les colonnes ou expressions à retourner", 1),
    ("la clause FROM", "indique les tables dont on tire les données", 1),
    ("la clause WHERE", "filtre les lignes avant tout regroupement", 2),
    ("la clause GROUP BY", "regroupe les lignes ayant les mêmes valeurs pour appliquer des agrégats", 3),
    ("la clause HAVING", "filtre les groupes après le calcul des agrégats", 4),
    ("la clause ORDER BY", "trie le résultat selon une ou plusieurs colonnes", 2),
    ("le mot-clé DISTINCT", "supprime les lignes en double du résultat", 3),
    ("la commande INSERT", "ajoute de nouvelles lignes dans une table", 1),
    ("la commande UPDATE", "modifie des valeurs de lignes existantes", 2),
    ("la commande DELETE", "supprime des lignes d'une table", 2),
    ("la commande CREATE TABLE", "définit une nouvelle table avec ses colonnes", 2),
    ("la commande DROP TABLE", "supprime une table entière avec ses données", 3),
    ("une jointure interne (INNER JOIN)", "ne garde que les lignes qui ont une correspondance dans les deux tables", 3),
    ("une jointure externe gauche (LEFT JOIN)", "garde toutes les lignes de la table de gauche, avec NULL si pas de correspondance", 4),
    ("le produit cartésien (CROSS JOIN)", "associe chaque ligne d'une table à chaque ligne de l'autre", 4),
    ("une sous-requête", "est une requête imbriquée dans une autre", 3),
], cat="Bases de données", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

mcq(L2, "inf2-sql-m", [
    ("La colonne x d'une table T contient 3, 5, 4 et NULL. Que renvoie SELECT COUNT(x) FROM T ?", "3", ["4", "0", "12", "NULL"], "COUNT(colonne) ignore les valeurs NULL.", 4),
    ("La colonne x d'une table T contient 3, 5, 4 et NULL. Que renvoie SELECT COUNT(*) FROM T ?", "4", ["3", "12", "0", "NULL"], "COUNT(*) compte toutes les lignes.", 4),
    ("La colonne x contient 3, 5, 4 et NULL. Que renvoie SELECT AVG(x) FROM T ?", "4", ["3", "3,0", "NULL", "12"], "NULL est ignoré : (3+5+4) / 3 = 4.", 5),
    ("La colonne x contient 3, 5, 5 et 3. Que renvoie SELECT COUNT(DISTINCT x) FROM T ?", "2", ["4", "1", "3", "16"], "Seules les valeurs 3 et 5 sont distinctes.", 4),
    ("Pourquoi WHERE x = NULL ne retourne-t-il aucune ligne ?", "Parce qu'une comparaison avec NULL n'est jamais vraie ; il faut écrire x IS NULL", ["Parce que NULL est égal à zéro", "Parce que NULL est interdit en SQL", "Parce que x = NULL supprime les lignes", "Parce que WHERE ne sait filtrer que les textes"], "NULL signifie « inconnu » : toute comparaison ordinaire reste inconnue.", 5),
    ("A contient les identifiants 1, 2, 3 ; B contient les valeurs a_id 1, 1, 2. Combien de lignes retourne A INNER JOIN B ON A.id = B.a_id ?", "3", ["2", "4", "6", "1"], "L'id 1 correspond deux fois, l'id 2 une fois, l'id 3 aucune.", 4),
    ("Mêmes tables A (1, 2, 3) et B (a_id : 1, 1, 2). Combien de lignes retourne A LEFT JOIN B ON A.id = B.a_id ?", "4", ["3", "6", "2", "5"], "Les 3 correspondances plus la ligne de A sans correspondance (id 3).", 5),
    ("Une table A contient 3 lignes, une table B en contient 4. Combien de lignes retourne A CROSS JOIN B ?", "12", ["7", "3", "4", "1"], "3 × 4 combinaisons.", 3),
    ("T(cat, v) contient (a,1), (a,2), (b,5). Que renvoie SELECT cat, SUM(v) FROM T GROUP BY cat ?", "Deux lignes : (a, 3) et (b, 5)", ["Une seule ligne : (a, 8)", "Trois lignes : (a,1), (a,2), (b,5)", "Deux lignes : (a, 2) et (b, 5)", "Aucune ligne"], "Le groupe a vaut 1 + 2 = 3, le groupe b vaut 5.", 4),
    ("Pour garder uniquement les catégories dont la somme dépasse 10 après un GROUP BY, quelle clause faut-il ?", "HAVING", ["WHERE", "ORDER BY", "DISTINCT", "FROM"], "WHERE agit avant le regroupement, HAVING après.", 4),
    ("Quelle requête retourne les noms des étudiants de la table Etudiant(nom, note) dont la note est au moins de 10 ?", "SELECT nom FROM Etudiant WHERE note >= 10", ["SELECT nom WHERE Etudiant note >= 10", "SELECT * HAVING nom >= 10", "FROM Etudiant SELECT note >= 10", "SELECT nom FROM Etudiant GROUP note 10"], "SELECT, FROM, WHERE dans cet ordre.", 2),
    ("Quelle requête trie les étudiants de la table Etudiant(nom, note) du plus haut au plus bas score ?", "SELECT nom FROM Etudiant ORDER BY note DESC", ["SELECT nom FROM Etudiant ORDER BY note", "SELECT nom FROM Etudiant GROUP BY note", "SELECT nom FROM Etudiant WHERE note DESC", "SELECT nom FROM Etudiant DISTINCT note"], "ORDER BY est ascendant par défaut ; DESC inverse.", 3),
    ("Quelle requête compte les étudiants par filière dans Etudiant(nom, filiere) ?", "SELECT filiere, COUNT(*) FROM Etudiant GROUP BY filiere", ["SELECT COUNT(*) FROM Etudiant WHERE filiere", "SELECT filiere FROM Etudiant ORDER BY COUNT", "SELECT filiere, COUNT(*) FROM Etudiant ORDER BY filiere HAVING", "SELECT filiere FROM Etudiant HAVING COUNT(*)"], "On regroupe par filière et on compte chaque groupe.", 4),
    ("Quelle est la différence entre UNION et UNION ALL ?", "UNION supprime les doublons, UNION ALL les conserve", ["UNION ALL supprime les doublons, UNION les conserve", "UNION ne marche qu'avec une table", "UNION ALL trie obligatoirement", "Aucune différence"], "UNION ALL est en général plus rapide car il ne dédoublonne pas.", 5),
    ("Quelle requête supprime toutes les lignes de Etudiant dont la note vaut 0 sans supprimer la table ?", "DELETE FROM Etudiant WHERE note = 0", ["DROP TABLE Etudiant", "DROP FROM Etudiant WHERE note = 0", "REMOVE Etudiant WHERE note = 0", "DELETE TABLE Etudiant note = 0"], "DELETE supprime des lignes ; DROP supprime la table.", 3),
    ("Dans une table Etudiant(id, nom, ville), quelle colonne est le meilleur choix de clé primaire ?", "id", ["nom", "ville", "nom et ville ensemble", "aucune"], "Les noms et villes peuvent se répéter, un identifiant unique non.", 2),
    ("Une table Commande(id_cmd, id_produit, nom_produit, quantite) a pour clé (id_cmd, id_produit). Quel défaut de normalisation présente-t-elle ?", "nom_produit dépend de id_produit seul : violation de la 2FN", ["Aucun défaut", "La table n'est pas en 1FN", "quantite dépend de nom_produit", "id_cmd est NULL"], "Une dépendance partielle vis-à-vis de la clé viole la 2FN.", 5),
    ("Une table Employe(id, id_service, nom_service) a pour clé id. nom_service dépend de id_service. Quel défaut présente-t-elle ?", "Une dépendance transitive : violation de la 3FN", ["Une violation de la 1FN", "Une violation de la clé étrangère", "Aucun défaut", "Une absence de clé primaire"], "id → id_service → nom_service.", 5),
    ("Une colonne « téléphones » contient « 690111111, 677222222 » dans une même cellule. Quelle forme normale est violée ?", "La première forme normale", ["La deuxième forme normale", "La troisième forme normale", "Aucune", "La forme de Boyce-Codd seulement"], "Une cellule doit contenir une valeur atomique.", 4),
    ("Comment représente-t-on dans un modèle relationnel qu'un étudiant suit plusieurs cours et qu'un cours a plusieurs étudiants ?", "Par une table d'association avec deux clés étrangères", ["Par une seule colonne contenant une liste", "Par une clé primaire double dans Etudiant", "En répétant le cours dans chaque ligne d'étudiant", "Il est impossible de le représenter"], "Une association n-n devient une table à part.", 4),
], cat="Bases de données", src=S)

table(L2, "inf2-acid", [
    ("une transaction", "regroupe des opérations traitées comme un tout, validé ou annulé", 2),
    ("l'atomicité", "garantit qu'une transaction est exécutée entièrement ou pas du tout", 3),
    ("la cohérence (ACID)", "garantit qu'une transaction fait passer la base d'un état valide à un autre état valide", 4),
    ("l'isolation", "garantit que des transactions simultanées n'interfèrent pas de façon visible", 4),
    ("la durabilité", "garantit qu'une transaction validée survit à une panne", 3),
    ("COMMIT", "valide définitivement les modifications d'une transaction", 3),
    ("ROLLBACK", "annule les modifications d'une transaction non validée", 3),
    ("un verrou", "empêche temporairement d'autres transactions d'accéder à une donnée", 4),
    ("une lecture sale (dirty read)", "lit une donnée modifiée par une transaction qui n'est pas encore validée", 5),
], cat="Bases de données", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

mcq(L2, "inf2-acid-m", [
    ("Lors d'un virement, le compte A est débité puis une panne survient avant le crédit du compte B. Quelle propriété ACID doit éviter cette situation ?", "L'atomicité", ["La durabilité", "L'isolation", "L'indexation", "La normalisation"], "Tout ou rien : le débit doit être annulé.", 3),
    ("Après un COMMIT, le serveur de la base s'arrête brutalement. Quelle propriété garantit que la transaction est conservée au redémarrage ?", "La durabilité", ["L'atomicité", "La cohérence", "L'isolation", "La normalisation"], "Les changements validés sont stockés de façon durable.", 3),
    ("Que signifie l'acronyme ACID ?", "Atomicité, cohérence, isolation, durabilité", ["Accès, contrôle, indexation, données", "Atomicité, concurrence, indexation, durabilité", "Adressage, cohérence, isolation, disque", "Authentification, confidentialité, intégrité, disponibilité"], "Ce sont les quatre propriétés d'une transaction fiable.", 2),
], cat="Bases de données", src=S)

table(L2, "inf2-prog", [
    ("le typage statique", "vérifie les types avant l'exécution, en général à la compilation", 3),
    ("le typage dynamique", "vérifie les types pendant l'exécution", 3),
    ("la portée (scope) d'une variable", "est la zone du programme où son nom est visible", 3),
    ("une variable locale", "n'existe que dans la fonction ou le bloc où elle est déclarée", 2),
    ("une variable globale", "est visible depuis l'ensemble du programme", 2),
    ("le passage par valeur", "transmet à la fonction une copie de l'argument", 3),
    ("le passage par référence", "transmet à la fonction un accès à l'argument d'origine", 4),
    ("la pile d'appels", "mémorise les appels de fonctions en cours et leurs variables locales", 4),
    ("le tas (heap)", "est la zone de mémoire où se font les allocations dynamiques", 4),
    ("une fuite de mémoire", "survient quand de la mémoire allouée n'est jamais rendue", 4),
    ("une exception", "signale une situation anormale et déroute l'exécution vers un gestionnaire d'erreur", 3),
    ("un pointeur", "est une variable qui contient l'adresse d'une autre donnée en mémoire", 4),
    ("le ramasse-miettes (garbage collector)", "libère automatiquement la mémoire des objets devenus inaccessibles", 4),
    ("la programmation fonctionnelle", "met en avant l'évaluation de fonctions et évite les modifications d'état", 4),
    ("la programmation impérative", "décrit le calcul comme une suite d'instructions qui modifient l'état", 3),
], cat="Programmation", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

table(L2, "inf2-oo", [
    ("une classe", "est un modèle qui décrit les attributs et les méthodes de ses objets", 2),
    ("un objet (instance)", "est un exemplaire créé à partir d'une classe", 2),
    ("un attribut d'objet", "est une donnée propre à chaque instance d'une classe", 2),
    ("une méthode", "est une fonction définie dans une classe qui agit sur ses objets", 2),
    ("un constructeur", "est la méthode appelée à la création d'un objet pour l'initialiser", 3),
    ("l'encapsulation", "cache l'état interne d'un objet derrière une interface contrôlée", 3),
    ("l'héritage", "permet à une classe de reprendre et d'étendre une autre classe", 3),
    ("le polymorphisme", "fait varier le comportement d'un même appel selon le type réel de l'objet", 4),
    ("l'abstraction", "met en avant l'essentiel d'un concept en masquant les détails", 3),
    ("une classe abstraite", "ne peut pas être instanciée directement et sert de base aux sous-classes", 4),
    ("une interface", "est un contrat qui liste des méthodes que les classes doivent fournir", 4),
    ("la composition", "fait contenir à un objet d'autres objets, selon une relation « a un »", 4),
    ("la redéfinition (override)", "remplace dans une sous-classe une méthode héritée", 4),
    ("la surcharge (overload)", "propose, sous un même nom, plusieurs méthodes aux paramètres différents", 4),
    ("un membre privé", "n'est accessible que depuis l'intérieur de la classe", 3),
], cat="Programmation", src=S, fwd=FW, rev=RV, expl="{A} {b}.")

mcq(L2, "inf2-prog-m", [
    ("Que vaut factorielle(5) définie par n! = n × (n−1)! avec 0! = 1 ?", "120", ["24", "25", "60", "720"], "5 × 4 × 3 × 2 × 1.", 2),
    ("Quelle est la valeur du 6e terme (F6) de la suite de Fibonacci avec F0 = 0 et F1 = 1 ?", "8", ["5", "13", "6", "7"], "0, 1, 1, 2, 3, 5, 8.", 3),
    ("Combien d'appels (le premier compris) fait un calcul récursif naïf de fib(5) avec fib(0) = 0 et fib(1) = 1 ?", "15", ["5", "8", "10", "25"], "Le nombre d'appels c(n) = 1 + c(n−1) + c(n−2), avec c(0) = c(1) = 1, donne c(5) = 15.", 5),
    ("Quel est le rôle du cas de base dans une fonction récursive ?", "Arrêter la récursion en renvoyant un résultat sans nouvel appel", ["Accélérer chaque appel", "Augmenter la taille de la pile", "Remplacer les paramètres", "Rendre la fonction globale"], "Sans cas de base, les appels ne s'arrêtent jamais.", 2),
    ("Que se passe-t-il si une fonction récursive n'atteint jamais son cas de base ?", "La pile d'appels déborde", ["Le programme se termine normalement", "Le résultat vaut zéro", "Le compilateur corrige la fonction", "Les variables deviennent globales"], "Chaque appel occupe de la pile jusqu'à épuisement.", 3),
    ("Quel est le PGCD de 48 et 18 ?", "6", ["3", "9", "12", "2"], "Euclide : 48 = 2×18 + 12 ; 18 = 1×12 + 6 ; 12 = 2×6.", 3),
    ("Combien de déplacements minimaux faut-il pour résoudre les tours de Hanoï à 3 disques ?", "7", ["3", "6", "8", "9"], "Le minimum est 2^n − 1.", 4),
    ("Combien de déplacements minimaux pour les tours de Hanoï à 5 disques ?", "31", ["10", "25", "32", "15"], "2^5 − 1.", 5),
    ("Une classe Animal définit parler() ; Chien et Chat la redéfinissent. On appelle parler() sur chaque élément d'une liste d'animaux. De quel principe relève le fait que chacun réagisse à sa façon ?", "Le polymorphisme", ["L'encapsulation", "La surcharge de constructeur", "La portée de variable", "La récursivité"], "Le comportement dépend du type réel de l'objet.", 3),
    ("Une classe Voiture possède un attribut Moteur. Quelle relation est-ce ?", "Une composition (relation « a un »)", ["Un héritage (relation « est un »)", "Une interface", "Une surcharge", "Une exception"], "La voiture contient un moteur.", 3),
    ("Une classe Chien hérite d'Animal. Quelle relation traduit l'héritage ?", "« Est un » : un chien est un animal", ["« A un » : un chien a un animal", "« Utilise » : un chien utilise un animal", "« Détruit » : un chien détruit un animal", "Aucune relation"], "L'héritage modélise une spécialisation.", 3),
    ("Pourquoi déclarer un attribut privé et fournir des méthodes d'accès ?", "Pour contrôler et protéger l'état de l'objet (encapsulation)", ["Pour augmenter la vitesse du processeur", "Pour supprimer les méthodes", "Pour hériter plus facilement", "Pour éviter d'utiliser des classes"], "Les accès contrôlés conservent la cohérence de l'objet.", 3),
    ("Quelle est la différence entre un compilateur et un interpréteur ?", "Le compilateur traduit tout avant l'exécution, l'interpréteur traduit et exécute pas à pas", ["Le compilateur ne traduit que les commentaires", "L'interpréteur produit toujours du binaire autonome", "Il n'y en a aucune", "L'interpréteur ne s'applique qu'au matériel"], "Les deux approches existent et se combinent parfois.", 3),
    ("Que contiendra l'entier non signé sur 8 bits valant 255 après l'ajout de 1, dans une arithmétique qui boucle sur 8 bits ?", "0", ["256", "1", "255", "−1"], "255 + 1 = 256 dépasse la capacité et revient à 0.", 5),
    ("Quel est le résultat de la division entière −7 par 2 dans un langage qui tronque vers zéro ?", "−3", ["−4", "−3,5", "3", "−2"], "Ce comportement dépend du langage ; certains arrondissent vers le bas.", 5),
    ("Dans une évaluation « paresseuse » de A ET B, que se passe-t-il si A est faux ?", "B n'est pas évalué, car le résultat est déjà faux", ["B est évalué en premier", "Le programme s'arrête", "A est remplacé par vrai", "Le résultat est vrai"], "C'est l'évaluation en court-circuit, présente dans de nombreux langages.", 4),
    ("Qu'est-ce qu'un paramètre formel par rapport à un argument effectif ?", "Le paramètre est le nom dans la définition de la fonction, l'argument la valeur fournie à l'appel", ["Le paramètre est la valeur, l'argument le nom", "Ce sont des synonymes stricts", "L'argument n'existe que dans les classes", "Le paramètre est un fichier"], "Le premier est déclaré, le second est transmis.", 4),
], cat="Programmation", src=S)
