import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'secrets.dart';
import 'servicos/jarvis_brain_service.dart';
import 'servicos/jarvis_actions_router.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MaterialApp(
    debugShowCheckedModeBanner: false,
    home: WakeWordTestPage(),
  ));
}

class WakeWordTestPage extends StatefulWidget {
  const WakeWordTestPage({super.key});

  @override
  State<WakeWordTestPage> createState() => _WakeWordTestPageState();
}

class _WakeWordTestPageState extends State<WakeWordTestPage> {
  static const MethodChannel _channel = MethodChannel('com.example.jarvis/wakeword');

  late final JarvisBrainService _brain;
  late final JarvisDispatcher _dispatcher;

  bool _isListening = false;
  String _status = 'Parado';
  String _ultimoJson = 'Nenhum comando recebido ainda.';

  @override
  void initState() {
    super.initState();
    _brain = JarvisBrainService(geminiApiKey);
    _dispatcher = JarvisDispatcher();
    _channel.setMethodCallHandler(_handleNativeCall);
    _pedirPermissoes();
  }

  Future<void> _pedirPermissoes() async {
    await [
      Permission.microphone,
      Permission.notification,
    ].request();
  }

  Future<void> _handleNativeCall(MethodCall call) async {
    switch (call.method) {
      case 'onWakeWordDetected':
        setState(() => _status = 'Palavra "Hey Jarvis" detetada!');
        break;

      case 'onListeningCommand':
        setState(() => _status = 'A gravar comando de voz...');
        break;

      case 'onCommandAudioRecorded':
        final Uint8List wavBytes = call.arguments as Uint8List;
        setState(() => _status = 'A enviar áudio para o Gemini...');

        try {
          // 1. Envia áudio WAV para o Gemini e recebe o Map JSON
          final Map<String, dynamic> jsonResult = await _brain.processAudio(wavBytes);

          setState(() {
            _status = 'Comando processado com sucesso!';
            _ultimoJson = jsonResult.toString();
          });

          // 2. Despacha a ação diretamente (YouTube, navegação, controlo multimédia)
          await _dispatcher.execute(jsonResult);
        } catch (e) {
          setState(() => _status = 'Erro no processamento: $e');
        }
        break;

      default:
        debugPrint('Método não reconhecido: ${call.method}');
        break;
    }
  }

  Future<void> _toggleListening() async {
    if (_isListening) {
      await _channel.invokeMethod('stopListening');
      setState(() {
        _isListening = false;
        _status = 'Parado';
      });
    } else {
      await _channel.invokeMethod('startListening');
      setState(() {
        _isListening = true;
        _status = 'À escuta de "Hey Jarvis"...';
      });
    }
  }

  @override
  void dispose() {
    _dispatcher.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Jarvis - Teste de Voz'),
        centerTitle: true,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ElevatedButton.icon(
              onPressed: _toggleListening,
              icon: Icon(_isListening ? Icons.stop : Icons.mic),
              label: Text(_isListening ? 'Parar Escuta' : 'Iniciar Escuta'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 14),
                backgroundColor: _isListening ? Colors.redAccent : Colors.blueAccent,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 20),
            Card(
              elevation: 2,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Estado:',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      _status,
                      style: TextStyle(
                        fontSize: 15,
                        color: _isListening ? Colors.green[700] : Colors.grey[700],
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              'Último JSON Retornado:',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.grey[100],
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.grey[300]!),
                ),
                child: SingleChildScrollView(
                  child: Text(
                    _ultimoJson,
                    style: const TextStyle(fontFamily: 'monospace', fontSize: 13),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}