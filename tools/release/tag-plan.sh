#!/usr/bin/env bash
# Plan d'étiquetage (tags git annotés) des versions de CastBridge : tv-<v>, phone-<v>, owner-<v>.
#
# DRY-RUN par défaut : affiche les commandes « git tag -a » / « git push origin <tag> » qu'il EXÉCUTERAIT.
#   bash tools/release/tag-plan.sh                 # plan (aucune écriture)
#   bash tools/release/tag-plan.sh --apply         # crée les tags LOCAUX prouvés (jamais de push)
#   bash tools/release/tag-plan.sh --apply --push  # crée puis pousse chaque tag (git push origin refs/tags/<tag>)
#   bash tools/release/tag-plan.sh --app tv        # une seule application (tv, phone, owner ; répétable)
#
# Principe : une version est « prouvée » quand la ligne <app>.versionName=<v> de version.properties est introduite
# par UN SEUL commit de l'historique premier-parent de HEAD (git log -p, recoupé par git log -G), et que ce commit
# contient aussi la ligne versionCode. Sinon elle est « ambiguë » et listée à part, sans commande exécutable.
# Un tag existant n'est JAMAIS déplacé ni écrasé. Rien ne touche à `main` ni à une branche : seuls des tags sont créés.
# Variable CB_REPO : dépôt à traiter (défaut : celui du script). Compatible bash 3.2 (macOS).
set -Eeuo pipefail

APPLY=0; PUSH=0; ONLY=""
while [ $# -gt 0 ]; do
    case "$1" in
        --apply) APPLY=1 ;;
        --push) PUSH=1 ;;
        --app) shift; [ $# -gt 0 ] || { echo "--app attend tv, phone ou owner" >&2; exit 2; }
               case "$1" in tv|phone|owner) ONLY="$ONLY $1" ;; *) echo "application inconnue : $1" >&2; exit 2 ;; esac ;;
        -h|--help) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "option inconnue : $1 (voir --help)" >&2; exit 2 ;;
    esac
    shift
done

REPO="${CB_REPO:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$REPO"
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERREUR : $REPO n'est pas un dépôt git" >&2; exit 2; }
PROPS="version.properties"
git cat-file -e "HEAD:$PROPS" 2>/dev/null || { echo "ERREUR : $PROPS absent de HEAD" >&2; exit 2; }
[ "$PUSH" = 1 ] && [ "$APPLY" = 0 ] && echo "note : --push sans --apply ne fait qu'afficher les commandes de push."
if [ "$APPLY" = 1 ]; then
    branch="$(git rev-parse --abbrev-ref HEAD)"
    [ "$branch" != "main" ] || { echo "ERREUR : refus d'appliquer depuis la branche main" >&2; exit 2; }
fi

APPS="tv phone owner"
[ -z "$ONLY" ] || APPS="$ONLY"
label() { case "$1" in tv) echo "CastBridge-TV" ;; phone) echo "CastBridge" ;; owner) echo "CastBridge Propriétaire" ;; esac; }

additions() { # $1 = app -> lignes « sha version » (commit qui AJOUTE la ligne versionName)
    git log --first-parent --reverse --format='#C# %H' -p -U0 -- "$PROPS" |
        awk -v app="$1" 'BEGIN { pat = "^\\+" app "\\.versionName=" }
            /^#C# / { c = substr($0, 5); next }
            $0 ~ pat { v = $0; sub(/^[^=]*=/, "", v); print c " " v }'
}
code_at() { git show "$1:$PROPS" 2>/dev/null | sed -n "s/^$2\\.versionCode=//p" | sed -n 1p; }
name_at() { git show "$1:$PROPS" 2>/dev/null | sed -n "s/^$2\\.versionName=//p" | sed -n 1p; }

EXIST_OK=""; PROVEN_CMDS=""; PROVEN_COUNT=0; AMBIG=""; NOTES=""; SKIPPED=""
add_amb() { AMBIG="$AMBIG
  - $1"; }
add_note() { NOTES="$NOTES
  - $1"; }

for app in $APPS; do
    lbl="$(label "$app")"
    list="$(additions "$app")"
    [ -n "$list" ] || continue
    prev_code=""; prev_name=""
    for v in $(printf '%s\n' "$list" | awk '{print $2}' | awk '!seen[$0]++'); do
        n="$(printf '%s\n' "$list" | awk -v v="$v" '$2 == v' | wc -l | tr -d ' ')"
        tag="$app-$v"
        if [ "$n" != 1 ]; then
            shas="$(printf '%s\n' "$list" | awk -v v="$v" '$2 == v {print substr($1,1,10)}' | tr '\n' ' ')"
            add_amb "$tag : introduite par $n commits (réintroduction ?) : $shas"
            continue
        fi
        sha="$(printf '%s\n' "$list" | awk -v v="$v" '$2 == v {print $1}')"
        vre="$(printf '%s' "$v" | sed 's/[.]/\\./g')"
        first="$(git log --first-parent --reverse --format=%H -G"^$app\\.versionName=$vre\$" -- "$PROPS" | sed -n 1p)"
        code="$(code_at "$sha" "$app")"
        if [ "$first" != "$sha" ]; then
            add_amb "$tag : git log -G désigne $(printf '%.10s' "$first") mais -p désigne $(printf '%.10s' "$sha")"
            continue
        fi
        if [ -z "$code" ]; then
            add_amb "$tag : commit $(printf '%.10s' "$sha") sans ligne $app.versionCode"
            continue
        fi
        # variantes de version déjà au même nom dans un tag existant ?
        if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
            existing="$(git rev-list -n 1 "refs/tags/$tag")"
            if [ "$existing" = "$sha" ]; then
                EXIST_OK="$EXIST_OK $tag"
                SKIPPED="$SKIPPED
  - $tag existe déjà sur le bon commit ($(printf '%.10s' "$sha"))"
            else
                SKIPPED="$SKIPPED
  - $tag existe déjà sur $(printf '%.10s' "$existing") (différent du commit prouvé $(printf '%.10s' "$sha")) : NON déplacé, à examiner"
            fi
        else
            date="$(git log -1 --format=%ad --date=short "$sha")"
            subject="$(git log -1 --format=%s "$sha" | cut -c1-90)"
            msg="$lbl $v (versionCode $code)"
            [ "$app" != tv ] || msg="$msg ; variante verrouillée $v-verrouillee (code $((code + 1)))"
            msg="$msg ; version.properties introduite par $(printf '%.10s' "$sha") le $date"
            PROVEN_COUNT=$((PROVEN_COUNT + 1))
            PROVEN_CMDS="$PROVEN_CMDS
$app|$tag|$sha|$msg|$date|$code|$subject"
        fi
        # trous : versions intermédiaires construites mais jamais commitées
        step=1; [ "$app" != tv ] || step=2
        if [ -n "$prev_code" ] && [ $((code - prev_code)) -gt "$step" ]; then
            add_note "$app : $prev_name ($prev_code) -> $v ($code) : codes et versions intermédiaires construits sans commit de version.properties (non étiquetables)"
        fi
        prev_code="$code"; prev_name="$v"
    done
    # valeur de l'arbre de travail absente de l'historique
    if [ -f "$PROPS" ]; then
        wv="$(sed -n "s/^$app\\.versionName=//p" "$PROPS" | head -1)"
        if [ -n "$wv" ] && ! printf '%s\n' "$list" | awk '{print $2}' | grep -qxF "$wv"; then
            add_amb "$app-$wv : présente seulement dans l'arbre de travail (non commitée) : à étiqueter après le commit de version"
        fi
    fi
done

# Versions citées par les sujets de commits d'AVANT version.properties (non prouvables : numéros dans le texte seulement).
SUBJ=""; SUBJ_SEEN=" "
subj_add() { # app token(version[@code]) sha subject : première citation seulement
    local v="${2%@*}" c=""; case "$2" in *@*) c=" (code ${2#*@})" ;; esac
    case "$SUBJ_SEEN" in *" $1-$v "*) return 0 ;; esac
    SUBJ_SEEN="$SUBJ_SEEN$1-$v "
    SUBJ="$SUBJ
  - $1-$v ? commit $(printf '%.10s' "$3")$c : « $(printf '%.70s' "$4") »"
}
firstprops="$(git log --first-parent --diff-filter=A --format=%H -- "$PROPS" | tail -1)"
if [ -n "$firstprops" ] && git rev-parse -q --verify "$firstprops^" >/dev/null; then
    while IFS="$(printf '\t')" read -r sha subj; do
        [ -n "$sha" ] || continue
        case " $APPS " in *" tv "*)
            for tok in $(printf '%s\n' "$subj" | grep -oE '(CastBridge-TV|TV) [0-9]+\.[0-9]+(\.[0-9]+)?( \([0-9]+\))?' | sed -E 's/^(CastBridge-TV|TV) //; s/ \(/@/; s/\)//' || true); do
                subj_add tv "$tok" "$sha" "$subj"
            done ;;
        esac
        case " $APPS " in *" phone "*)
            for tok in $(printf '%s\n' "$subj" | grep -oE '(phone|CastBridge|[Vv]ersion) [0-9]+\.[0-9]+(\.[0-9]+)?-beta( \([0-9]+\))?' | sed -E 's/^[A-Za-z]+ //; s/ \(/@/; s/\)//' || true); do
                subj_add phone "$tok" "$sha" "$subj"
            done ;;
        esac
    done < <(git log --first-parent --reverse --format='%H%x09%s' "$firstprops^")
fi

echo "== Plan d'étiquetage ($( [ "$APPLY" = 1 ] && echo APPLY || echo DRY-RUN )) : $REPO"
echo "HEAD : $(git rev-parse --short HEAD) sur $(git rev-parse --abbrev-ref HEAD) ; version.properties lu dans l'historique premier-parent"
echo
echo "== Versions PROUVÉES, tag à créer : $PROVEN_COUNT"
created=0
printf '%s\n' "$PROVEN_CMDS" | while IFS='|' read -r app tag sha msg date code subject; do
    [ -n "$tag" ] || continue
    printf 'git tag -a %s %s -m "%s"\n' "$tag" "$sha" "$msg"
    printf 'git push origin refs/tags/%s%s\n' "$tag" "$( [ "$PUSH" = 1 ] || echo '   # seulement avec --apply --push' )"
done
if [ -n "$SKIPPED" ]; then echo; echo "== Tags déjà présents (jamais déplacés) :$SKIPPED"; fi
echo
echo "== Versions AMBIGUËS (aucune commande ; décision du propriétaire) :${AMBIG:-
  (aucune)}"
if [ -n "$NOTES" ]; then echo; echo "== Trous constatés :$NOTES"; fi
if [ -n "$SUBJ" ]; then
    echo; echo "== Versions citées seulement dans des sujets de commits (avant version.properties) : NON prouvées, aucun tag proposé :$SUBJ"
fi

if [ "$APPLY" = 1 ]; then
    echo
    echo "== Application"
    printf '%s\n' "$PROVEN_CMDS" | while IFS='|' read -r app tag sha msg date code subject; do
        [ -n "$tag" ] || continue
        if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then echo "ignoré (existe) : $tag"; continue; fi
        git tag -a "$tag" "$sha" -m "$msg"
        echo "créé : $tag -> $(printf '%.10s' "$sha")"
        if [ "$PUSH" = 1 ]; then git push origin "refs/tags/$tag"; fi
    done
    if [ "$PUSH" = 1 ]; then
        for tag in $EXIST_OK; do
            if [ -z "$(git ls-remote origin "refs/tags/$tag" | awk '{print $1}')" ]; then
                git push origin "refs/tags/$tag"; echo "poussé (tag local déjà présent) : $tag"
            fi
        done
    fi
else
    echo
    echo "DRY-RUN : rien n'a été créé. Relancer avec --apply pour créer les tags locaux (et --push pour les publier)."
fi
