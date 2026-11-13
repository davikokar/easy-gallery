"""The quality-check queries: 6 concepts x 4 shipped languages (en, it, de, es)."""

CONCEPTS = {
    "castles": {"en": "castles", "it": "castelli", "de": "Burgen", "es": "castillos"},
    "beach_sunset": {"en": "a beach at sunset", "it": "una spiaggia al tramonto", "de": "ein Strand bei Sonnenuntergang",
                     "es": "una playa al atardecer"},
    "dog": {"en": "a dog", "it": "un cane", "de": "ein Hund", "es": "un perro"},
    "food": {"en": "food on a table", "it": "cibo su un tavolo", "de": "Essen auf einem Tisch",
             "es": "comida sobre una mesa"},
    "snow_mountains": {"en": "snow-covered mountains", "it": "montagne innevate", "de": "schneebedeckte Berge",
                       "es": "montañas nevadas"},
    "city_night": {"en": "a city street at night", "it": "una strada di città di notte",
                   "de": "eine Straße in der Stadt bei Nacht", "es": "una calle de la ciudad de noche"},
}
LANGS = ["en", "it", "de", "es"]
QUERIES = [{"concept": c, "lang": lang, "text": CONCEPTS[c][lang]} for c in CONCEPTS for lang in LANGS]
