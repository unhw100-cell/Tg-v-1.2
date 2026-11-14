//! RustGram Media Engine
//! Image resizing, voice note waveform sampling, file cache management and mime helpers.


use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MediaDownloadProgress {
    pub file_id: i32,
    pub expected_size: i64,
    pub downloaded_size: i64,
    pub is_completed: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VoiceWaveform {
    pub samples: Vec<u8>,
}

impl VoiceWaveform {
    /// Resample arbitrary audio peak levels into a Telegram-standard 100-bar waveform (0-31 range per 5 bits)
    pub fn generate_normalized(raw_peaks: &[f32], target_bars: usize) -> Self {
        if raw_peaks.is_empty() {
            return Self {
                samples: vec![4; target_bars],
            };
        }

        let mut samples = Vec::with_capacity(target_bars);
        let step = (raw_peaks.len() as f32) / (target_bars as f32);

        for i in 0..target_bars {
            let idx = (i as f32 * step).min((raw_peaks.len() - 1) as f32) as usize;
            let peak = raw_peaks[idx].clamp(0.0, 1.0);
            let val = (peak * 31.0).round() as u8;
            samples.push(val.max(1));
        }

        Self { samples }
    }
}

pub fn calculate_aspect_fit(src_w: u32, src_h: u32, max_w: u32, max_h: u32) -> (u32, u32) {
    if src_w == 0 || src_h == 0 {
        return (max_w, max_h);
    }
    let ratio = (src_w as f32) / (src_h as f32);
    if ratio > 1.0 {
        let w = max_w.min(src_w);
        let h = ((w as f32) / ratio).round() as u32;
        (w, h.min(max_h).max(1))
    } else {
        let h = max_h.min(src_h);
        let w = ((h as f32) * ratio).round() as u32;
        (w.min(max_w).max(1), h)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_voice_waveform_generation() {
        let peaks = vec![0.1, 0.4, 0.8, 1.0, 0.5, 0.2];
        let wf = VoiceWaveform::generate_normalized(&peaks, 10);
        assert_eq!(wf.samples.len(), 10);
        for &s in &wf.samples {
            assert!(s <= 31);
        }
    }

    #[test]
    fn test_aspect_fit() {
        let (w, h) = calculate_aspect_fit(1920, 1080, 800, 800);
        assert_eq!(w, 800);
        assert_eq!(h, 450);
    }
}
