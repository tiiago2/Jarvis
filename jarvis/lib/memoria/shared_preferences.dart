import 'package:shared_preferences/shared_preferences.dart';

class GuardarVariavel {
  static const String _assistantKey = 'selected_word';

  // 1. Guardar a escolha
  static Future<void> saveWord(String assistantName) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_assistantKey, assistantName);
  }

  // 2. Ler a escolha guardada
  static Future<String> getWord() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_assistantKey) ?? 'jarvis';
  }
}