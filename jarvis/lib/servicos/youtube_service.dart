import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import 'package:youtube_explode_dart/youtube_explode_dart.dart';

class YouTubeAudioService {
  final YoutubeExplode _yt = YoutubeExplode();
  final AudioPlayer _player = AudioPlayer();

  // Cabeçalhos que impedem o erro HTTP 403 Forbidden nos servidores do YouTube
  static const Map<String, String> _networkHeaders = {
    'User-Agent':
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    'Referer': 'https://www.youtube.com/',
  };

  Future<void> playFromSearch(String query) async {
    try {
      debugPrint('[YouTube] A pesquisar por: $query');
      final searchList = await _yt.search.search(query);

      if (searchList.isEmpty) {
        debugPrint('[YouTube] Nenhum resultado encontrado.');
        return;
      }

      final video = searchList.first;
      debugPrint('[YouTube] Selecionado: ${video.title}');

      // Obtém os manifestos de áudio disponíveis
      final manifest = await _yt.videos.streamsClient.getManifest(video.id);

      // Prioriza áudio em formato MP4/M4A (AAC), mais compatível com o ExoPlayer que WebM
      final audioStream = manifest.audioOnly.firstWhere(
        (s) => s.container.name.toLowerCase() == 'mp4',
        orElse: () => manifest.audioOnly.withHighestBitrate(),
      );

      debugPrint('[YouTube] A carregar stream (${audioStream.container.name}, ${audioStream.bitrate})...');

      // Garante que para faixas anteriores antes de carregar nova fonte
      await _player.stop();

      // Injeta a URI e os cabeçalhos de browser diretamente no ExoPlayer
      await _player.setAudioSource(
        AudioSource.uri(
          audioStream.url,
          headers: _networkHeaders,
        ),
      );

      await _player.play();
      debugPrint('[YouTube] Reprodução iniciada!');
    } catch (e, stack) {
      debugPrint('[YouTube ERRO]: $e');
      debugPrint('$stack');
    }
  }

  Future<void> pause() async => await _player.pause();
  Future<void> resume() async => await _player.play();
  Future<void> stop() async => await _player.stop();

  void dispose() {
    _player.dispose();
    _yt.close();
  }
}