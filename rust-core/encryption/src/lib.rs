//! RustGram Encryption Engine
//! Cryptographic utilities, SHA-256 digests, 2FA cloud password hash computation and session tokens.

use rand::RngCore;

use sha2::{Digest, Sha256};

pub struct TelegramCrypto;

impl TelegramCrypto {
    /// Calculate standard SHA-256 digest
    pub fn sha256(data: &[u8]) -> [u8; 32] {
        let mut hasher = Sha256::new();
        hasher.update(data);
        hasher.finalize().into()
    }

    /// Calculate SHA-256 in hex string representation
    pub fn sha256_hex(data: &[u8]) -> String {
        let hash = Self::sha256(data);
        hash.iter().map(|b| format!("{:02x}", b)).collect()
    }

    /// Generate cryptographically secure random session bytes
    pub fn generate_random_bytes(len: usize) -> Vec<u8> {
        let mut bytes = vec![0u8; len];
        rand::thread_rng().fill_bytes(&mut bytes);
        bytes
    }

    /// Compute 2FA password hash with salt (Telegram Cloud Password algorithm)
    pub fn compute_password_hash(password: &str, salt1: &[u8], salt2: &[u8]) -> Vec<u8> {
        // Step 1: hash password with salt1
        let mut h1 = Sha256::new();
        h1.update(salt1);
        h1.update(password.as_bytes());
        h1.update(salt1);
        let ph1 = h1.finalize();

        // Step 2: hash with salt2
        let mut h2 = Sha256::new();
        h2.update(salt2);
        h2.update(ph1);
        h2.update(salt2);
        h2.finalize().to_vec()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sha256() {
        let hash = TelegramCrypto::sha256_hex(b"Telegram");
        assert_eq!(
            hash,
            "acdd1e734125f341604c0efbabdcc4c4b0597e8f6235d66c2445edd1812838c1"
        );
    }

    #[test]
    fn test_random_bytes() {
        let b1 = TelegramCrypto::generate_random_bytes(32);
        let b2 = TelegramCrypto::generate_random_bytes(32);
        assert_eq!(b1.len(), 32);
        assert_ne!(b1, b2);
    }

    #[test]
    fn test_password_hash() {
        let hash = TelegramCrypto::compute_password_hash("mysecretpass", b"salt1", b"salt2");
        assert_eq!(hash.len(), 32);
    }
}
