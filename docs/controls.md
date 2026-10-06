# Excavator Controls & Operation Manual

## Two-Handed Ergonomic Layout (ISO Excavator Standards)

Real excavators are operated with two primary joysticks:
- **Left Joystick (Left Hand)**: Cab Swing (Obrót wieżyczki) & Dipper Stick (Przedramię).
- **Right Joystick (Right Hand)**: Main Boom (Wysięgnik główny) & Bucket (Łyżka).
- **Track Pedals/Levers**: Crawler tracks (Gąsienice).

**Peterwolf's Groundworks Excavator** maps this real-world two-handed layout directly to your keyboard:
- **Left Hand**: On **`WASD`**
- **Right Hand**: On **`Arrow Keys`** (`↑`, `↓`, `←`, `→`) or `T`/`G`
- **Thumb/Toggle**:
  - **`X`**: Przełącznik trybu lewej ręki: **JAZDA** (Gąsienice) $\longleftrightarrow$ **RAMIĘ** (Obrót & Przedramię)
  - **`Z`**: Przełącznik osprzętu: **STANDARDOWA ŁYŻKA** -> **DUŻA ŁYŻKA** -> **MŁOT PNEUMATYCZNY**
  - **`C`**: Młot chwilowo podczas trzymania klawisza
  - **`2x C`**: Włącza lub wyłącza ciągłą pracę młota

---

## 1. TRYB RAMIENIA (Arm Mode)

Obie ręce pracują symultanicznie jak na dwóch joystickach:

| Ręka / Kontroler | Klawisze | Funkcja | ISO Excavator Function |
|---|---|---|---|
| **Lewa ręka (WASD)** | `A` / `D` | **Obrót kabiny** w lewo / w prawo | Cab Swing Left / Right |
| **Lewa ręka (WASD)** | `W` / `S` | **Przedramię** wysuwanie / przyciąganie | Dipper Stick Out / In |
| **Prawa ręka (Strzałki)** | `↑` / `↓` | **Główne ramię** podnoszenie / opuszczanie | Main Boom Up / Down |
| **Prawa ręka (Strzałki & T/G)** | `←` / `T` | **Łyżka przyciągnięta** do mnie (zamknięta z towarem) | Bucket Curl In (holds cargo) |
| **Prawa ręka (Strzałki & T/G)** | `→` / `G` | **Łyżka odpuszczona** od gracza (odwrócona, wysyp) | Bucket Dump Out (pours cargo) |

*(Pomocnicze skróty: `R`/`F` mogą być także używane do przedramienia).*

---

## 2. TRYB JAZDY (Drive Mode) — Przełączany klawiszem `X`

Lewa ręka prowadzi gąsienice po terenie, a prawa ręka zachowuje pełną kontrolę nad wysokością ramienia i łyżki:

| Ręka / Kontroler | Klawisze | Funkcja | Działanie |
|---|---|---|---|
| **Lewa ręka (WASD)** | `W` / `S` | **Jazda gąsienicami** | Obie gąsienice do przodu / do tyłu |
| **Lewa ręka (WASD)** | `A` / `D` | **Skręt gąsienicami** | Skręt w ruchu lub obrót w miejscu (pivot) |
| **Prawa ręka (Strzałki)** | `↑` / `↓` | **Wysięgnik (Boom)** | Podnieś ramię podczas jazdy, aby nie haczyło |
| **Prawa ręka (Strzałki)** | `←` / `→` | **Łyżka (Bucket)** | Zwiń lub otwórz łyżkę w transporcie |

---

## 3. ZMIANA OSPRZĘTU - Klawisz `Z`

Klawisz **`Z`** cyklicznie zmienia osprzęt roboczy koparki za pomocą szybkozłącza:

1. **Łyżka Standardowa Skrawania (256 jednostek / $0.500\text{ m}^3$)**:
   - Szerokość 0.75m, 5 utwardzanych zębów dłutowych.
   - Idealna do precyzyjnych wykopów liniowych, fundamentów i rowów.
   - Przepustowość kopania: 32 jednostki/tick.

2. **Łyżka Duża Masowa / Podsiębierna ($512\text{ jednostek} / 1.000\text{ m}^3$ — $2\times$ większa!)**:
   - Szerokość 1.25m, 7 utwardzanych zębów, głęboka czasza o podwójnej kubaturze (cały pełny blok Minecrafta!).
   - Idealna do masowych robót ziemnych, załadunku urobku i formowania wałów.
   - Przepustowość kopania i wysypu: aż 64 jednostki/tick ($2\times$ szybszy urobek!).
   - Wygląd modelu w grze zmienia się natychmiast na potężniejszą, szerszą łyżkę.


3. **Młot pneumatyczny do skał**:
   - Uruchomienie chwilowe: przytrzymaj `C`.
   - Praca ciągła: szybko naciśnij `C` dwa razy. Ponowne `2x C` wyłącza zatrzask.
   - Każde skuteczne uderzenie kruszy 128/512 jednostek, czyli dokładnie 1/4 bloku.
   - Kamień jest kruszony do granularnego `cobblestone` i odkładany obok kutego miejsca.
   - Młot może przesuwać już wykuty granularny `cobblestone`, ale nigdy nie nabiera materiału do pojemnika łyżki.
   - Aktywna praca młota powoduje dokładnie 36% obciążenia maszyny.
   - Punkt kontaktu końcówki jest liczony z tej samej macierzy kinematycznej co model 3D, dzięki czemu miejsce uderzenia odpowiada widocznej końcówce.
