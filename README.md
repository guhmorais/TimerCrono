# Floating Timer — Cronômetros e Temporizadores Flutuantes para Android

Aplicativo Android nativo desenvolvido em **Kotlin** e **Jetpack Compose**, projetado para criar, personalizar e executar múltiplos cronômetros progressivos e temporizadores regressivos em **janelas flutuantes reais (`WindowManager` / `TYPE_APPLICATION_OVERLAY`)** sobre qualquer aplicativo, além de oferecer **5 temporizadores independentes por notificação**.

---

## 1. Arquitetura Técnica e Estrutura de Pacotes

O projeto segue os princípios de **Clean Architecture + MVVM**, separando rigorosamente a lógica matemática de temporização, o gerenciamento de janelas nativas, o serviço em primeiro plano, a persistência e a interface declarativa:

```text
app/src/main/java/com/example/
├── FloatingTimerApplication.kt       # Inicialização de dependências e canais de notificação
├── MainActivity.kt                   # Host da UI Compose, navegação e controle de tela ligada
├── domain/
│   └── TimerModels.kt                # Modelos (TimerConfig, TimerStatus, TimerCategory, TimeFormatted)
├── timer/
│   ├── ClockProvider.kt              # Abstração de relógio monotônico (SystemClock.elapsedRealtime)
│   ├── TimerCalculator.kt            # Motor matemático puro (progressivo, regressivo, repetição e pós-reboot)
│   └── TimerEngine.kt                # Coordenador central reativo (StateFlow + SharedFlow)
├── overlay/
│   ├── SevenSegmentDigitDrawer.kt    # Renderizador vetorial de 7 segmentos LED/LCD + centésimos menores
│   ├── OverlayGestureController.kt   # Máquina de estados de gestos (1 toque, 2 toques, arrastar, segurar)
│   ├── FloatingOverlayView.kt        # View Android nativa exibida sobre outros aplicativos
│   └── OverlayWindowManager.kt       # Coordenação de múltiplas janelas no WindowManager
├── notification/
│   └── TimerNotificationHelper.kt    # Canais, notificações individuais deduplicadas e alertas
├── service/
│   ├── FloatingTimerService.kt       # Foreground Service adaptativo e controle de WakeLock/Vibração
│   └── TimerActionReceiver.kt        # BroadcastReceiver para botões de notificação e BOOT_COMPLETED
├── data/
│   ├── TimerDatabase.kt              # Banco de dados Room (TimerEntity, TimerDao)
│   └── TimerRepository.kt            # Repositório de persistência estruturada dos 10 cronômetros
├── settings/
│   └── SettingsRepository.kt         # Preferências gerais com Jetpack DataStore Preferences
└── ui/
    ├── TimerViewModel.kt             # ViewModel conectando UI, TimerEngine e FloatingTimerService
    ├── components/
    │   ├── TimerPreviewDisplay.kt    # Prévia fiel da janela flutuante em Compose
    │   └── DialogsAndModals.kt       # Seletor de cores RGB/Hex, seletor de intervalo e tutorial
    ├── screens/
    │   ├── MainScreen.kt             # Abas "Flutuando" (5 cards) e "Notificações" (5 cards)
    │   ├── EditTimerScreen.kt        # Edição individual completa com prévia em tempo real
    │   └── SettingsScreen.kt         # Configurações gerais, bloqueio de posição e restauração
    └── theme/                        # Tema Material Design 3 (Claro, Escuro e Automático)
```

---

## 2. Destaques Técnicos

1. **Alta Precisão Monotônica (`SystemClock.elapsedRealtime()`)**:
   - A contagem nunca depende de incrementar contadores a cada frame visual. O tempo decorrido ou restante é sempre calculado a partir da referência monotônica `elapsedRealtime()`, garantindo zero desvio (*drift*) mesmo sob carga do sistema ou com a tela bloqueada.
2. **Repetição Automática sem Perda de Ciclo**:
   - No modo regressivo com repetição automática, o tempo restante no ciclo atual é calculado via aritmética modular monotônica (`targetDuration - (totalElapsed % targetDuration)`), preservando a precisão exata entre múltiplos ciclos.
3. **Política Explícita Pós-Reinicialização (`BOOT_COMPLETED`)**:
   - Como `elapsedRealtime()` é reiniciado no boot do dispositivo, `TimerCalculator.reconcileAfterReboot()` detecta a descontinuidade monotônica e preserva o tempo acumulado convertendo o estado para `PAUSED` de forma segura.
4. **Gestos Livres de Conflito nas Janelas Flutuantes**:
   - **1 toque**: Iniciar / Pausar / Continuar.
   - **2 toques**: Zerar (com opção de confirmação embutida na própria janela flutuante).
   - **Arrastar**: Move a janela respeitando os limites úteis da tela (retrato e paisagem), cancelando qualquer toque simples ou duplo pendente.
   - **Pressionar e segurar**: Abre a barra compacta de controles adicionais na janela.

---

## 3. Como Compilar e Executar Testes

- **Compilar APK Debug**:
  ```bash
  gradle assembleDebug
  ```
- **Executar Testes Unitários e de Integração (Robolectric)**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
