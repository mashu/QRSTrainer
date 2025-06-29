# Morse Code Trainer

A comprehensive Android application for learning Morse code using the Koch method training approach.

## Features

### 🎯 Koch Method Training
- Progressive character introduction following the standard Koch sequence
- Starts with K and M, gradually adds new characters
- Adaptive progression based on user performance
- 41 characters total including letters, numbers, and common punctuation

### 🎵 Audio Generation
- Real-time Morse code audio synthesis
- Customizable WPM (Words Per Minute) speed (5-50 WPM)
- Farnsworth timing for character spacing
- Configurable repeat count (1-5 times)
- High-quality sine wave generation at 600Hz

### 📊 Progress Tracking
- Individual character statistics (correct/incorrect counts)
- Overall accuracy tracking
- Session-based progress monitoring
- Adaptive weighting system that emphasizes problematic characters
- Visual progress indicators with color coding

### ⚙️ Configurable Settings
- **Speed Control**: 5-50 WPM
- **Level Management**: Manual level selection or automatic progression
- **Group Size**: Configurable minimum (1-10) and maximum (1-15) character groups
- **Timing**: Answer timeout (3-30 seconds)
- **Repeat Spacing**: Configurable pause between repeats (0.5-10 seconds)
- **Progression**: Required correct answers to advance (1-50)
- **Level Locking**: Option to stay at current level for focused practice

### 📈 Statistics & Analytics
- Character-specific accuracy rates
- Attempts counter per character
- Visual progress bars
- Streak tracking
- Weighted character selection based on performance
- **Real-time validation**: Instant character-by-character feedback with color coding
- **Previous answer display**: Brief history of last answer for reference

## How to Use

1. **Start Training**: Open the app and navigate to the "Trainer" section
2. **Listen & Type**: Audio plays automatically - you can start typing immediately as you recognize characters
3. **Real-time Feedback**: Characters turn green (correct) or red (incorrect) as you type
4. **Flexible Input**: Use the on-screen keyboard during or after audio playback
5. **Auto-submit**: Answer is checked automatically when you've entered enough characters
6. **Progress**: Correct answers advance your progress toward the next level
7. **Review**: Check the "Progress" section to see detailed statistics
8. **Configure**: Adjust settings in the "Settings" section to customize your experience

## Koch Method Sequence

The app follows the standard Koch method character sequence:

```
K M U R E S N A P T L W I . J Z = F O Y , V G 5 / Q 9 2 H 3 8 B ? 4 7 C 1 D 6 0 X
```

## Training Philosophy

The Koch method is proven effective because it:
- Maintains consistent high-speed character transmission
- Uses Farnsworth timing for comfortable character spacing
- Gradually increases character set complexity
- Adapts to individual learning patterns through weighted selection

## Technical Details

- **Minimum Android Version**: API 31 (Android 12)
- **Target SDK**: 35
- **Audio**: Pure sine wave synthesis using AudioTrack
- **Data Persistence**: SharedPreferences with JSON serialization
- **Architecture**: MVVM with data binding
- **UI**: Material Design components with CardView and RecyclerView

## Building

This is a standard Android Studio project. Clone the repository and open in Android Studio to build and run.

## License

This project is released under the MIT License. See the LICENSE file for details.

## About

This software was created during my personal journey learning Morse code. It's shared with no expectations, simply in case it might help someone else. It comes with no warranty of suitability and makes no claims of being anything more than a personal project made available to others. Bug reports, issues, and contributions are welcome on the project page: https://github.com/mashu/QRSTrainer 