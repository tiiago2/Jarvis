// lib/services/jarvis_brain_service.dart
import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter/foundation.dart';
import 'package:google_generative_ai/google_generative_ai.dart';

class JarvisBrainService {
  final GenerativeModel _model;

  JarvisBrainService(String apiKey)
      : _model = GenerativeModel(
          model: 'gemini-3.5-flash',
          apiKey: apiKey,
          generationConfig: GenerationConfig(
            responseMimeType: 'application/json',
            temperature: 0.0,
          ),
          systemInstruction: Content.system('''
            És o motor de comandos do Jarvis. O áudio pode vir em PT, EN ou ES.
            Analisa o áudio e devolve ESTRITAMENTE um JSON com este formato:
            {
              "category": "NAVIGATE" | "PLAY_SONG" | "PLAY_PLAYLIST" | "PAUSE_SONG" | "STOP_SONG" | "FORWARD_SONG" | "REWIND_SONG" | "SHUTDOWN" | "OTHERS",
              "spoken_response": "Texto curto para falar ao utilizador (obrigatório se category for OTHERS)",
              "params": {
                "query": "nome da faixa, artista ou playlist pretendida",
                "destination": "local ou morada se category for NAVIGATE, se a categoria for algo relacionado com musica deverá aparecer aqui qual o local de onde se quer colocar música, caso não seja referido assume Spotify, esta categoria podera incluir 'Web', 'Youtube' e 'Soundcloud'"
              }
            }

            Regras estritas:
            - category tem de assumir unicamente um dos valores listados.
            - "PLAY_SONG": música ou artista avulso (ex: "toca Starboy", "mete Queen").
            - "PLAY_PLAYLIST": se o utilizador disser explicitamente a palavra playlist.
            - "NAVIGATE": rotas, direções ou distâncias. Preenche destination.
            - "PAUSE_SONG", "STOP_SONG", "FORWARD_SONG", "REWIND_SONG": ações do leitor de media.
            - "SHUTDOWN": se pedir para desligar/fechar o assistente.
            - "OTHERS": qualquer dúvida geral, conversa ou cálculo. Responde em spoken_response em no máximo 2 frases.
          '''),
        );

  Future<Map<String, dynamic>> processAudio(Uint8List wavBytes) async {
    // 1. Valida se o áudio gravou mesmo som ou se veio vazio
    if (wavBytes.length <= 44) {
      throw Exception("Áudio vazio! O microfone só enviou ${wavBytes.length} bytes.");
    }

    // 2. Tenta a chamada à API do Gemini
    final audioPart = DataPart('audio/wav', wavBytes);
    final response = await _model.generateContent([
      Content.multi([
        TextPart('Classifica a ação falada:'),
        audioPart,
      ])
    ]);

    String? jsonText = response.text;
    if (jsonText == null || jsonText.trim().isEmpty) {
      throw Exception("O Gemini devolveu uma resposta vazia.");
    }

    jsonText = jsonText.trim();
    if (jsonText.startsWith('```')) {
      jsonText = jsonText.replaceAll(RegExp(r'^```(json)?|```$', multiLine: true), '').trim();
    }

    return jsonDecode(jsonText) as Map<String, dynamic>;
  }
}