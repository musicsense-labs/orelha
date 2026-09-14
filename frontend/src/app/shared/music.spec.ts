import { describe, expect, it } from 'vitest';
import { chordName, formatTime, keyName, noteName } from './music';

describe('music helpers', () => {
  it('renders chord names with quality and slash bass', () => {
    expect(chordName(0, 'MAJ')).toBe('C');
    expect(chordName(1, 'MIN7')).toBe('C♯m7');
    expect(chordName(4, 'POWER')).toBe('E5');
    expect(chordName(9, 'MIN', 8)).toBe('Am/A♭');
    expect(chordName(9, 'MIN', 9)).toBe('Am');
    expect(chordName(null, 'NO_CHORD')).toBe('N');
  });

  it('names keys and notes', () => {
    expect(keyName(7, 'MAJOR')).toBe('G major');
    expect(keyName(6, 'PHRYGIAN_DOMINANT')).toBe('F♯ phrygian dominant');
    expect(noteName(null)).toBe('—');
    expect(noteName(13)).toBe('C♯');
  });

  it('formats time as m:ss', () => {
    expect(formatTime(0)).toBe('0:00');
    expect(formatTime(198.4)).toBe('3:18');
  });
});
