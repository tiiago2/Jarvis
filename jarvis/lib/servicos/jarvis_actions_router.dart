import 'youtube_service.dart';

class JarvisDispatcher {
  final YouTubeAudioService _audioService;

  JarvisDispatcher({YouTubeAudioService? audioService})
      : _audioService = audioService ?? YouTubeAudioService();

  Future<void> execute(Map<String, dynamic> json) async {
    final String category = json['category'] as String? ?? 'OTHERS';
    final Map<String, dynamic> params = (json['params'] as Map<String, dynamic>?) ?? {};
    final String destination = params['destination'] as String? ?? '';

    switch(category){
      case 'PLAY_SONG':
        if(destination == 'Spotify'){

        }
        if(destination == 'Web'){
          //final titulo = await _audioService.playFromSearch(query);

          //nao esta operacional
        }
      break;
      case 'PLAY_PLAYLIST':
      break;
      case 'NAVIGATE':
      break;
      case 'PAUSE_SONG':
      break;
      case 'SHUTDOWN':
      break;
      case 'OTHERS':
      break;
    }
  }

  void dispose() {
    _audioService.dispose();
  }
}

