import json
import os
import logging

logger = logging.getLogger(__name__)

class TranslationStore:
    def __init__(self, translations_dir: str):
        self.translations_dir = translations_dir
        self.translations = {}
        self._load_all_translations()

    def _load_all_translations(self):
        if not os.path.exists(self.translations_dir):
            logger.warning(f"Translations directory {self.translations_dir} does not exist.")
            return

        for filename in os.listdir(self.translations_dir):
            if filename.endswith(".json"):
                lang_code = filename[:-5]
                filepath = os.path.join(self.translations_dir, filename)
                try:
                    with open(filepath, 'r', encoding='utf-8') as f:
                        data = json.load(f)
                        if not isinstance(data, dict):
                            logger.error(f"Invalid format in {filename}. Expected a JSON object.")
                            continue
                        
                        self.translations[lang_code] = data
                        logger.info(f"Loaded {len(data)} translations for '{lang_code}'")
                except Exception as e:
                    logger.error(f"Failed to load translation file {filename}: {e}")

    def get_translation(self, label: str, language: str) -> str:
        if language not in self.translations:
            # Fallback to English if language is not supported
            language = "en"
        
        lang_dict = self.translations.get(language, {})
        
        if label in lang_dict:
            return lang_dict[label]
        
        # Fallback to English canonical label if missing in target language
        en_dict = self.translations.get("en", {})
        if label in en_dict:
            logger.warning(f"Missing translation for '{label}' in language '{language}'. Falling back to English.")
            return en_dict[label]
        
        # Fallback to original label if unknown
        logger.warning(f"Unknown label '{label}'. No translation found.")
        return label

# Global instance for the service
_store = None

def init_translation_store(translations_dir: str):
    global _store
    _store = TranslationStore(translations_dir)

def get_translation(label: str, language: str = "en") -> str:
    if _store is None:
        logger.warning("Translation store not initialized. Returning original label.")
        return label
    return _store.get_translation(label, language)
