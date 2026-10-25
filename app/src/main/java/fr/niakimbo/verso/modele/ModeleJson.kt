package fr.niakimbo.verso.modele

import fr.niakimbo.verso.matrix.MatrixFrame
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lecture / écriture du format JSON des modèles (VERSO_SPEC §3.4).
 *
 * ```json
 * {
 *   "id": "coeur",
 *   "nom": "Cœur",
 *   "categorie": "Émotions",         (optionnel : rayon de la bibliothèque)
 *   "type": "anime",
 *   "dynamique": "horloge",          (optionnel : modèle calculé en direct)
 *   "frames": [
 *     { "pixels": [0, 0, 255, …], "dureeMs": 120 },
 *     { "dessin": [".....X.....", …], "dureeMs": 120 }
 *   ]
 * }
 * ```
 *
 * Chaque image s'écrit au choix :
 * - `pixels` : 169 luminosités 0-255, ligne par ligne (tableau, ou texte séparé par virgules/espaces) ;
 * - `dessin` : 13 lignes de 13 caractères, pratique à écrire à la main :
 *   `.` ou espace = éteint, `1`…`9` = luminosité croissante, `X` ou `#` = pleine luminosité.
 *
 * L'export ([toJson]) utilise toujours `pixels` : c'est le format qu'utiliseront l'éditeur et Trame.
 */
object ModeleJson {

    fun parse(text: String): Modele = parse(JSONObject(text))

    fun parse(json: JSONObject): Modele {
        val id = json.getString("id").trim()
        try {
            val nom = json.optString("nom", id).ifBlank { id }
            val type = Modele.Type.fromJson(json.optString("type", Modele.Type.STATIQUE.json))
            val frames = json.getJSONArray("frames")
            val images = (0 until frames.length()).map { i ->
                try {
                    parseImage(frames.getJSONObject(i))
                } catch (e: Exception) {
                    throw IllegalArgumentException("image ${i + 1} : ${e.message}", e)
                }
            }
            val dynamique = json.optString("dynamique", "").takeIf { it.isNotBlank() }?.let(Modele.Dynamique::fromJson)
            val categorie = json.optString("categorie", "").takeIf { it.isNotBlank() }
            return Modele(id = id, nom = nom, type = type, images = images, dynamique = dynamique, categorie = categorie)
        } catch (e: Exception) {
            throw IllegalArgumentException("Modèle « $id » : ${e.message}", e)
        }
    }

    private fun parseImage(json: JSONObject): Modele.Image {
        val frame = when {
            json.has("pixels") -> MatrixFrame(parsePixels(json.get("pixels")))
            json.has("dessin") -> parseDessin(json.getJSONArray("dessin"))
            else -> throw IllegalArgumentException("ni « pixels » ni « dessin »")
        }
        val duree = json.optLong("dureeMs", Modele.DUREE_PAR_DEFAUT_MS)
        return Modele.Image(frame, duree)
    }

    private fun parsePixels(value: Any): IntArray {
        val values: List<Int> = when (value) {
            is JSONArray -> (0 until value.length()).map { value.getInt(it) }
            is String -> value.split(',', ' ', '\n', '\t').filter { it.isNotBlank() }.map { it.trim().toInt() }
            else -> throw IllegalArgumentException("« pixels » doit être un tableau ou un texte")
        }
        require(values.size == MatrixFrame.SIZE) { "${MatrixFrame.SIZE} pixels attendus (reçu ${values.size})" }
        values.forEach { require(it in 0..MatrixFrame.MAX) { "luminosité hors limites : $it (0-${MatrixFrame.MAX})" } }
        return values.toIntArray()
    }

    private fun parseDessin(rows: JSONArray): MatrixFrame {
        require(rows.length() == MatrixFrame.SIDE) { "${MatrixFrame.SIDE} lignes attendues (reçu ${rows.length()})" }
        val px = IntArray(MatrixFrame.SIZE)
        for (y in 0 until MatrixFrame.SIDE) {
            val row = rows.getString(y)
            require(row.length == MatrixFrame.SIDE) {
                "ligne ${y + 1} : ${MatrixFrame.SIDE} caractères attendus (reçu ${row.length})"
            }
            row.forEachIndexed { x, c -> px[y * MatrixFrame.SIDE + x] = luminosite(c, y) }
        }
        return MatrixFrame(px)
    }

    private fun luminosite(c: Char, y: Int): Int = when (c) {
        '.', ' ' -> 0
        'X', 'x', '#' -> MatrixFrame.MAX
        in '1'..'9' -> (c - '0') * MatrixFrame.MAX / 9
        else -> throw IllegalArgumentException("ligne ${y + 1} : caractère inconnu « $c »")
    }

    fun toJson(modele: Modele): JSONObject = JSONObject().apply {
        put("id", modele.id)
        put("nom", modele.nom)
        modele.categorie?.let { put("categorie", it) }
        put("type", modele.type.json)
        modele.dynamique?.let { put("dynamique", it.json) }
        put("frames", JSONArray().apply {
            modele.images.forEach { image ->
                put(JSONObject().apply {
                    put("pixels", JSONArray().apply { image.frame.pixels.forEach { put(it) } })
                    put("dureeMs", image.dureeMs)
                })
            }
        })
    }
}
