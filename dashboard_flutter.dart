import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Estación Ambiental IoT',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0B0B0C),
        fontFamily: 'SF Pro Text',
      ),
      home: const DashboardScreen(),
    );
  }
}

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> with TickerProviderStateMixin {
  // Estados de Telemetría (Valores iniciales ficticios que cambian aleatoriamente)
  double _temperatura = 22.8;
  double _humedad = 58.3;
  int _co2 = 1450;
  int _tvoc = 120;
  DateTime _lastUpdate = DateTime.now();

  // Estados de control
  bool _extractorActivo = false;
  late AnimationController _fanAnimationController;

  // Estados de Pomodoro
  int _pomodoroTotalSeconds = 25 * 60;
  int _pomodoroRemainingSeconds = 25 * 60;
  bool _isPomodoroRunning = false;
  bool _isPomodoroPaused = false;
  Timer? _pomodoroTimer;

  // Estados de Alarma
  int _alarmaHora = 7;
  int _alarmaMinuto = 30;
  bool _alarmaActiva = true;

  // Timer para simular datos en tiempo real de Firebase
  Timer? _telemetrySimulationTimer;

  @override
  void initState() {
    super.initState();
    // Animación de rotación para el extractor de aire
    _fanAnimationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );

    // Simulación de actualización de datos de sensores
    _telemetrySimulationTimer = Timer.periodic(const Duration(seconds: 4), (timer) {
      if (mounted) {
        setState(() {
          _temperatura += (math.Random().nextDouble() - 0.5) * 0.4;
          _humedad += (math.Random().nextDouble() - 0.5) * 0.8;
          _co2 += (math.Random().nextInt(3) - 1) * 30;
          _tvoc += (math.Random().nextInt(3) - 1) * 5;
          _lastUpdate = DateTime.now();

          // Limitar valores dentro de rangos normales
          _temperatura = double.parse(_temperatura.toStringAsFixed(1));
          _humedad = double.parse(_humedad.toStringAsFixed(1));
          if (_temperatura < 15) _temperatura = 15;
          if (_temperatura > 35) _temperatura = 35;
          if (_humedad < 20) _humedad = 20;
          if (_humedad > 90) _humedad = 90;
          if (_co2 < 400) _co2 = 400;
          if (_co2 > 2500) _co2 = 2500;
        });
      }
    });
  }

  @override
  void dispose() {
    _fanAnimationController.dispose();
    _pomodoroTimer?.cancel();
    _telemetrySimulationTimer?.cancel();
    super.dispose();
  }

  // --- LÓGICA POMODORO ---
  void _startPomodoro() {
    _pomodoroTimer?.cancel();
    setState(() {
      _isPomodoroRunning = true;
      _isPomodoroPaused = false;
    });

    _pomodoroTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (mounted) {
        setState(() {
          if (_pomodoroRemainingSeconds > 0) {
            _pomodoroRemainingSeconds--;
          } else {
            _stopPomodoro();
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('🎉 ¡Pomodoro finalizado! Hora de descansar.'),
                backgroundColor: Color(0xFF00E676),
              ),
            );
          }
        });
      }
    });
  }

  void _pausePomodoro() {
    _pomodoroTimer?.cancel();
    setState(() {
      _isPomodoroPaused = true;
    });
  }

  void _stopPomodoro() {
    _pomodoroTimer?.cancel();
    setState(() {
      _isPomodoroRunning = false;
      _isPomodoroPaused = false;
      _pomodoroRemainingSeconds = _pomodoroTotalSeconds;
    });
  }

  void _setPomodoroDuration(int minutes) {
    if (!_isPomodoroRunning) {
      setState(() {
        _pomodoroTotalSeconds = minutes * 60;
        _pomodoroRemainingSeconds = _pomodoroTotalSeconds;
      });
    }
  }

  String _formatTime(int totalSeconds) {
    int minutes = totalSeconds ~/ 60;
    int seconds = totalSeconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }

  // --- LÓGICA EXTRACTOR ---
  void _toggleExtractor(bool value) {
    setState(() {
      _extractorActivo = value;
      if (_extractorActivo) {
        _fanAnimationController.repeat();
      } else {
        _fanAnimationController.stop();
      }
    });
  }

  // --- OBTENER ESTADO AMBIENTAL (HUMANIZADO) ---
  Map<String, dynamic> _getEnvironmentalStatus() {
    if (_co2 > 1800) {
      return {
        'status': 'ALERTA CRÍTICA',
        'color': const Color(0xFFFF5252),
        'message': 'Calidad del aire peligrosa. CO2 muy alto. Se aconseja ventilar e iniciar el extractor.',
        'icon': Icons.warning_amber_rounded,
      };
    } else if (_co2 > 1200) {
      return {
        'status': 'CALIDAD REGULAR',
        'color': const Color(0xFFFFC107),
        'message': 'Concentración moderada de CO2. Activa el extractor para renovar el aire.',
        'icon': Icons.info_outline,
      };
    } else {
      return {
        'status': 'ESTADO EXCELENTE',
        'color': const Color(0xFF00E676),
        'message': 'Todos los parámetros están en rango óptimo. Ambiente saludable.',
        'icon': Icons.check_circle_outline,
      };
    }
  }

  @override
  Widget build(BuildContext context) {
    final status = _getEnvironmentalStatus();

    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          physics: const BouncingScrollPhysics(),
          padding: const EdgeInsets.symmetric(horizontal: 20.0, vertical: 16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // HEADER
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'ESTACIÓN AMBIENTAL',
                        style: TextStyle(
                          fontSize: 11.0,
                          fontWeight: FontWeight.bold,
                          color: Colors.white.withOpacity(0.4),
                          letterSpacing: 1.5,
                        ),
                      ),
                      const SizedBox(height: 4),
                      const Text(
                        'Panel de Control',
                        style: TextStyle(
                          fontSize: 26.0,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                          letterSpacing: -0.5,
                        ),
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: const Color(0xFF141416),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: const Color(0xFF202023)),
                    ),
                    child: Icon(
                      Icons.settings_outlined,
                      color: Colors.white.withOpacity(0.7),
                      size: 20,
                    ),
                  )
                ],
              ),
              const SizedBox(height: 20),

              // TARJETA DE ESTADO GENERAL (HUMANIZADO)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: const Color(0xFF141416),
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(
                    color: (status['color'] as Color).withOpacity(0.2),
                    width: 1.5,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: (status['color'] as Color).withOpacity(0.03),
                      blurRadius: 20,
                      offset: const Offset(0, 10),
                    )
                  ],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(status['icon'] as IconData, color: status['color'] as Color, size: 22),
                        const SizedBox(width: 8),
                        Text(
                          status['status'] as String,
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.bold,
                            color: status['color'] as Color,
                            letterSpacing: 1.0,
                          ),
                        ),
                        const Spacer(),
                        Text(
                          'Último dato: Hace unos instantes',
                          style: TextStyle(
                            fontSize: 10,
                            color: Colors.white.withOpacity(0.3),
                          ),
                        )
                      ],
                    ),
                    const SizedBox(height: 12),
                    Text(
                      status['message'] as String,
                      style: const TextStyle(
                        fontSize: 14,
                        color: Color(0xFFF5F5F7),
                        height: 1.4,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // BENTO GRID - SENSORES (Temperatura, Humedad, CO2, TVOC)
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Columna Izquierda
                  Expanded(
                    child: Column(
                      children: [
                        // Tarjeta Temperatura (Grande)
                        _buildSensorCard(
                          title: 'Temperatura',
                          value: '$_temperatura °C',
                          subtitle: _temperatura > 26
                              ? 'Caluroso'
                              : (_temperatura < 18 ? 'Frío' : 'Normal'),
                          icon: Icons.thermostat_outlined,
                          iconColor: const Color(0xFF00E676),
                          height: 160,
                        ),
                        const SizedBox(height: 16),
                        // Tarjeta CO2
                        _buildSensorCard(
                          title: 'Calidad CO2',
                          value: '$_co2 ppm',
                          subtitle: _co2 > 1800
                              ? 'Crítico'
                              : (_co2 > 1200 ? 'Regular' : 'Excelente'),
                          icon: Icons.air_outlined,
                          iconColor: _co2 > 1800
                              ? const Color(0xFFFF5252)
                              : (_co2 > 1200 ? const Color(0xFFFFC107) : const Color(0xFF00E676)),
                          height: 140,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  // Columna Derecha
                  Expanded(
                    child: Column(
                      children: [
                        // Tarjeta Humedad
                        _buildSensorCard(
                          title: 'Humedad',
                          value: '$_humedad %',
                          subtitle: _humedad > 65
                              ? 'Húmedo'
                              : (_humedad < 35 ? 'Seco' : 'Confort'),
                          icon: Icons.water_drop_outlined,
                          iconColor: const Color(0xFF00E676),
                          height: 130,
                        ),
                        const SizedBox(height: 16),
                        // Tarjeta TVOC
                        _buildSensorCard(
                          title: 'Compuestos TVOC',
                          value: '$_tvoc mg',
                          subtitle: _tvoc > 300 ? 'Alto' : 'Seguro',
                          icon: Icons.biotech_outlined,
                          iconColor: const Color(0xFF00E676),
                          height: 170,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // TARJETA DE CONTROL: EXTRACTOR DE AIRE
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: const Color(0xFF141416),
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: const Color(0xFF202023)),
                ),
                child: Row(
                  children: [
                    // Ventilador Animado
                    AnimatedBuilder(
                      animation: _fanAnimationController,
                      builder: (context, child) {
                        return Transform.rotate(
                          angle: _fanAnimationController.value * 2 * math.pi,
                          child: child,
                        );
                      },
                      child: Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: _extractorActivo
                              ? const Color(0xFF00E676).withOpacity(0.1)
                              : Colors.white.withOpacity(0.02),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          Icons.toys_outlined,
                          color: _extractorActivo ? const Color(0xFF00E676) : Colors.white.withOpacity(0.4),
                          size: 28,
                        ),
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Extractor de Aire',
                            style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            _extractorActivo ? 'Encendido (Manual)' : 'Apagado / Automático',
                            style: TextStyle(
                              fontSize: 13,
                              color: _extractorActivo ? const Color(0xFF00E676) : Colors.white.withOpacity(0.4),
                            ),
                          ),
                        ],
                      ),
                    ),
                    Switch.adaptive(
                      value: _extractorActivo,
                      onChanged: _toggleExtractor,
                      activeColor: const Color(0xFF00E676),
                      activeTrackColor: const Color(0xFF00E676).withOpacity(0.2),
                      inactiveThumbColor: Colors.white.withOpacity(0.6),
                      inactiveTrackColor: Colors.white.withOpacity(0.1),
                    )
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // SECCIÓN: POMODORO
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: const Color(0xFF141416),
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: const Color(0xFF202023)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.timer_outlined, color: Colors.white.withOpacity(0.8), size: 20),
                        const SizedBox(width: 8),
                        const Text(
                          'Temporizador Pomodoro',
                          style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                        ),
                      ],
                    ),
                    const SizedBox(height: 20),
                    Row(
                      children: [
                        // Anillo circular
                        CustomPaint(
                          size: const Size(120, 120),
                          painter: PomodoroPainter(
                            progress: _pomodoroRemainingSeconds / _pomodoroTotalSeconds,
                            color: const Color(0xFF00E676),
                            bgColor: Colors.white.withOpacity(0.05),
                          ),
                          child: SizedBox(
                            width: 120,
                            height: 120,
                            child: Center(
                              child: Text(
                                _formatTime(_pomodoroRemainingSeconds),
                                style: const TextStyle(
                                  fontSize: 22,
                                  fontWeight: FontWeight.bold,
                                  fontFamily: 'SF Pro Display',
                                ),
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 24),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Text(
                                'Duración:',
                                style: TextStyle(fontSize: 12, color: Color(0xFF8E8E93)),
                              ),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: [5, 15, 25, 45].map((min) {
                                  final isSelected = _pomodoroTotalSeconds == min * 60;
                                  return GestureDetector(
                                    onTap: _isPomodoroRunning ? null : () => _setPomodoroDuration(min),
                                    child: AnimatedContainer(
                                      duration: const Duration(milliseconds: 200),
                                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                                      decoration: BoxDecoration(
                                        color: isSelected
                                            ? const Color(0xFF00E676).withOpacity(0.1)
                                            : Colors.white.withOpacity(0.02),
                                        borderRadius: BorderRadius.circular(8),
                                        border: Border.all(
                                          color: isSelected
                                              ? const Color(0xFF00E676).withOpacity(0.3)
                                              : Colors.white.withOpacity(0.05),
                                        ),
                                      ),
                                      child: Text(
                                        '$min m',
                                        style: TextStyle(
                                          fontSize: 12,
                                          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                                          color: isSelected ? const Color(0xFF00E676) : Colors.white.withOpacity(0.7),
                                        ),
                                      ),
                                    ),
                                  );
                                }).toList(),
                              ),
                              const SizedBox(height: 16),
                              Row(
                                children: [
                                  if (!_isPomodoroRunning || _isPomodoroPaused)
                                    IconButton(
                                      onPressed: _startPomodoro,
                                      icon: const Icon(Icons.play_arrow_rounded, color: Color(0xFF00E676), size: 30),
                                      padding: EdgeInsets.zero,
                                      constraints: const BoxConstraints(),
                                    )
                                  else
                                    IconButton(
                                      onPressed: _pausePomodoro,
                                      icon: const Icon(Icons.pause_rounded, color: Color(0xFFFFC107), size: 30),
                                      padding: EdgeInsets.zero,
                                      constraints: const BoxConstraints(),
                                    ),
                                  const SizedBox(width: 16),
                                  if (_isPomodoroRunning || _isPomodoroPaused)
                                    IconButton(
                                      onPressed: _stopPomodoro,
                                      icon: const Icon(Icons.stop_rounded, color: Color(0xFFFF5252), size: 30),
                                      padding: EdgeInsets.zero,
                                      constraints: const BoxConstraints(),
                                    ),
                                ],
                              )
                            ],
                          ),
                        )
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // ALARMA
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: const Color(0xFF141416),
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: const Color(0xFF202023)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.alarm, color: Colors.white.withOpacity(0.8), size: 20),
                        const SizedBox(width: 8),
                        const Text(
                          'Alarma de Estación',
                          style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                        ),
                        const Spacer(),
                        Switch.adaptive(
                          value: _alarmaActiva,
                          onChanged: (val) {
                            setState(() {
                              _alarmaActiva = val;
                            });
                          },
                          activeColor: const Color(0xFF00E676),
                          activeTrackColor: const Color(0xFF00E676).withOpacity(0.2),
                          inactiveThumbColor: Colors.white.withOpacity(0.6),
                          inactiveTrackColor: Colors.white.withOpacity(0.1),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        GestureDetector(
                          onTap: () async {
                            final TimeOfDay? picked = await showTimePicker(
                              context: context,
                              initialTime: TimeOfDay(hour: _alarmaHora, minute: _alarmaMinuto),
                              builder: (context, child) {
                                return Theme(
                                  data: ThemeData.dark().copyWith(
                                    colorScheme: const ColorScheme.dark(
                                      primary: Color(0xFF00E676),
                                      onPrimary: Colors.black,
                                      surface: Color(0xFF141416),
                                      onSurface: Colors.white,
                                    ),
                                    dialogBackgroundColor: const Color(0xFF0B0B0C),
                                  ),
                                  child: child!,
                                );
                              },
                            );
                            if (picked != null) {
                              setState(() {
                                _alarmaHora = picked.hour;
                                _alarmaMinuto = picked.minute;
                              });
                            }
                          },
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                            decoration: BoxDecoration(
                              color: Colors.white.withOpacity(0.02),
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(color: Colors.white.withOpacity(0.05)),
                            ),
                            child: Row(
                              children: [
                                Text(
                                  '${_alarmaHora.toString().padLeft(2, '0')}:${_alarmaMinuto.toString().padLeft(2, '0')}',
                                  style: const TextStyle(
                                    fontSize: 24,
                                    fontWeight: FontWeight.bold,
                                    fontFamily: 'SF Pro Display',
                                    color: Colors.white,
                                  ),
                                ),
                                const SizedBox(width: 10),
                                Icon(
                                  Icons.edit_calendar_outlined,
                                  size: 16,
                                  color: Colors.white.withOpacity(0.4),
                                )
                              ],
                            ),
                          ),
                        ),
                        Text(
                          _alarmaActiva ? 'Sonará a las ${_alarmaHora.toString().padLeft(2, '0')}:${_alarmaMinuto.toString().padLeft(2, '0')}' : 'Alarma desactivada',
                          style: TextStyle(
                            fontSize: 13,
                            color: Colors.white.withOpacity(_alarmaActiva ? 0.6 : 0.2),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSensorCard({
    required String title,
    required String value,
    required String subtitle,
    required IconData icon,
    required Color iconColor,
    required double height,
  }) {
    return Container(
      width: double.infinity,
      height: height,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: const Color(0xFF141416),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: const Color(0xFF202023)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                title.toUpperCase(),
                style: TextStyle(
                  fontSize: 10.0,
                  fontWeight: FontWeight.bold,
                  color: Colors.white.withOpacity(0.3),
                  letterSpacing: 1.0,
                ),
              ),
              Icon(icon, color: iconColor.withOpacity(0.8), size: 18),
            ],
          ),
          const Spacer(),
          Text(
            value,
            style: const TextStyle(
              fontSize: 26.0,
              fontWeight: FontWeight.w600,
              color: Colors.white,
              fontFamily: 'SF Pro Display',
              letterSpacing: -0.5,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            subtitle,
            style: TextStyle(
              fontSize: 12.0,
              color: iconColor.withOpacity(0.9),
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }
}

class PomodoroPainter extends CustomPainter {
  final double progress;
  final Color color;
  final Color bgColor;

  PomodoroPainter({
    required this.progress,
    required this.color,
    required this.bgColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = math.min(size.width / 2, size.height / 2) - 8;
    const strokeWidth = 8.0;

    final paintBg = Paint()
      ..color = bgColor
      ..strokeWidth = strokeWidth
      ..style = PaintingStyle.stroke;

    canvas.drawCircle(center, radius, paintBg);

    final paintProgress = Paint()
      ..color = color
      ..strokeWidth = strokeWidth
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    final sweepAngle = 2 * math.pi * progress;
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -math.pi / 2,
      sweepAngle,
      false,
      paintProgress,
    );
  }

  @override
  bool shouldRepaint(covariant PomodoroPainter oldDelegate) {
    return oldDelegate.progress != progress ||
        oldDelegate.color != color ||
        oldDelegate.bgColor != bgColor;
  }
}
