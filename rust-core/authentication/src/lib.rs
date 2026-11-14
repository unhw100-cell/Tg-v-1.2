//! RustGram Authentication Engine
//! Telegram MTProto / TDLib authorization state machine, 2FA cloud password management,
//! complete world country calling code directory (ITU-T E.164 + Telegram +888),
//! session restoration and multi-account state tracking.

use rustgram_core::{Result, RustGramError, UserId};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum AuthorizationState {
    WaitTdlibParameters,
    WaitPhoneNumber,
    WaitCode {
        phone_number: String,
        code_length: u32,
        send_type: String,
        timeout_sec: u32,
    },
    WaitPassword {
        password_hint: Option<String>,
        has_recovery_email: bool,
    },
    WaitRegistration {
        terms_of_service: Option<String>,
    },
    WaitOtherDeviceConfirmation {
        link: String,
    },
    Ready,
    LoggingOut,
    Closed,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CountryInfo {
    pub name: String,
    pub country_code: String,
    pub calling_code: String,
    pub flag_emoji: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AccountSession {
    pub account_id: u32,
    pub user_id: UserId,
    pub phone_number: String,
    pub display_name: String,
    pub username: Option<String>,
    pub avatar_path: Option<String>,
    pub is_active: bool,
    pub created_at: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuthManager {
    pub current_state: AuthorizationState,
    pub active_account_id: u32,
    pub accounts: Vec<AccountSession>,
    pub api_id: i32,
    pub api_hash: String,
}

impl Default for AuthManager {
    fn default() -> Self {
        Self {
            current_state: AuthorizationState::WaitTdlibParameters,
            active_account_id: 1,
            accounts: Vec::new(),
            api_id: 94575,
            api_hash: "a3406de8d17135cf542d8bd3540d613e".into(),
        }
    }
}

/// Complete list of world countries, territories, and Telegram Fragment anonymous numbers (+888).
/// Tuple format: (Display Name, ISO 3166-1 Alpha-2, E.164 Calling Code, Flag Emoji).
const WORLD_COUNTRIES: &[(&str, &str, &str, &str)] = &[
    ("Telegram Anonymous Numbers", "FT", "+888", "🏴‍☠️"),
    ("Afghanistan", "AF", "+93", "🇦🇫"),
    ("Åland Islands", "AX", "+358", "🇦🇽"),
    ("Albania", "AL", "+355", "🇦🇱"),
    ("Algeria", "DZ", "+213", "🇩🇿"),
    ("American Samoa", "AS", "+1684", "🇦🇸"),
    ("Andorra", "AD", "+376", "🇦🇩"),
    ("Angola", "AO", "+244", "🇦🇴"),
    ("Anguilla", "AI", "+1264", "🇦🇮"),
    ("Antigua & Barbuda", "AG", "+1268", "🇦🇬"),
    ("Argentina", "AR", "+54", "🇦🇷"),
    ("Armenia", "AM", "+374", "🇦🇲"),
    ("Aruba", "AW", "+297", "🇦🇼"),
    ("Australia", "AU", "+61", "🇦🇺"),
    ("Austria", "AT", "+43", "🇦🇹"),
    ("Azerbaijan", "AZ", "+994", "🇦🇿"),
    ("Bahamas", "BS", "+1242", "🇧🇸"),
    ("Bahrain", "BH", "+973", "🇧🇭"),
    ("Bangladesh", "BD", "+880", "🇧🇩"),
    ("Barbados", "BB", "+1246", "🇧🇧"),
    ("Belarus", "BY", "+375", "🇧🇾"),
    ("Belgium", "BE", "+32", "🇧🇪"),
    ("Belize", "BZ", "+501", "🇧🇿"),
    ("Benin", "BJ", "+229", "🇧🇯"),
    ("Bermuda", "BM", "+1441", "🇧🇲"),
    ("Bhutan", "BT", "+975", "🇧🇹"),
    ("Bolivia", "BO", "+591", "🇧🇴"),
    ("Bonaire, Sint Eustatius & Saba", "BQ", "+599", "🇧🇶"),
    ("Bosnia & Herzegovina", "BA", "+387", "🇧🇦"),
    ("Botswana", "BW", "+267", "🇧🇼"),
    ("Brazil", "BR", "+55", "🇧🇷"),
    ("British Indian Ocean Territory", "IO", "+246", "🇮🇴"),
    ("British Virgin Islands", "VG", "+1284", "🇻🇬"),
    ("Brunei Darussalam", "BN", "+673", "🇧🇳"),
    ("Bulgaria", "BG", "+359", "🇧🇬"),
    ("Burkina Faso", "BF", "+226", "🇧🇫"),
    ("Burundi", "BI", "+257", "🇧🇮"),
    ("Cambodia", "KH", "+855", "🇰🇭"),
    ("Cameroon", "CM", "+237", "🇨🇲"),
    ("Canada", "CA", "+1", "🇨🇦"),
    ("Cape Verde", "CV", "+238", "🇨🇻"),
    ("Cayman Islands", "KY", "+1345", "🇰🇾"),
    ("Central African Republic", "CF", "+236", "🇨🇫"),
    ("Chad", "TD", "+235", "🇹🇩"),
    ("Chile", "CL", "+56", "🇨🇱"),
    ("China", "CN", "+86", "🇨🇳"),
    ("Christmas Island", "CX", "+61", "🇨🇽"),
    ("Cocos (Keeling) Islands", "CC", "+61", "🇨🇨"),
    ("Colombia", "CO", "+57", "🇨🇴"),
    ("Comoros", "KM", "+269", "🇰🇲"),
    ("Congo (Brazzaville)", "CG", "+242", "🇨🇬"),
    ("Congo (Kinshasa / DRC)", "CD", "+243", "🇨🇩"),
    ("Cook Islands", "CK", "+682", "🇨🇰"),
    ("Costa Rica", "CR", "+506", "🇨🇷"),
    ("Côte d'Ivoire", "CI", "+225", "🇨🇮"),
    ("Croatia", "HR", "+385", "🇭🇷"),
    ("Cuba", "CU", "+53", "🇨🇺"),
    ("Curaçao", "CW", "+599", "🇨🇼"),
    ("Cyprus", "CY", "+357", "🇨🇾"),
    ("Czechia", "CZ", "+420", "🇨🇿"),
    ("Denmark", "DK", "+45", "🇩🇰"),
    ("Djibouti", "DJ", "+253", "🇩🇯"),
    ("Dominica", "DM", "+1767", "🇩🇲"),
    ("Dominican Republic", "DO", "+1809", "🇩🇴"),
    ("Ecuador", "EC", "+593", "🇪🇨"),
    ("Egypt", "EG", "+20", "🇪🇬"),
    ("El Salvador", "SV", "+503", "🇸🇻"),
    ("Equatorial Guinea", "GQ", "+240", "🇬🇶"),
    ("Eritrea", "ER", "+291", "🇪🇷"),
    ("Estonia", "EE", "+372", "🇪🇪"),
    ("Eswatini", "SZ", "+268", "🇸🇿"),
    ("Ethiopia", "ET", "+251", "🇪🇹"),
    ("Falkland Islands", "FK", "+500", "🇫🇰"),
    ("Faroe Islands", "FO", "+298", "🇫🇴"),
    ("Fiji", "FJ", "+679", "🇫🇯"),
    ("Finland", "FI", "+358", "🇫🇮"),
    ("France", "FR", "+33", "🇫🇷"),
    ("French Guiana", "GF", "+594", "🇬🇫"),
    ("French Polynesia", "PF", "+689", "🇵🇫"),
    ("Gabon", "GA", "+241", "🇬🇦"),
    ("Gambia", "GM", "+220", "🇬🇲"),
    ("Georgia", "GE", "+995", "🇬🇪"),
    ("Germany", "DE", "+49", "🇩🇪"),
    ("Ghana", "GH", "+233", "🇬🇭"),
    ("Gibraltar", "GI", "+350", "🇬🇮"),
    ("Greece", "GR", "+30", "🇬🇷"),
    ("Greenland", "GL", "+299", "🇬🇱"),
    ("Grenada", "GD", "+1473", "🇬🇩"),
    ("Guadeloupe", "GP", "+590", "🇬🇵"),
    ("Guam", "GU", "+1671", "🇬🇺"),
    ("Guatemala", "GT", "+502", "🇬🇹"),
    ("Guernsey", "GG", "+44", "🇬🇬"),
    ("Guinea", "GN", "+224", "🇬🇳"),
    ("Guinea-Bissau", "GW", "+245", "🇬🇼"),
    ("Guyana", "GY", "+592", "🇬🇾"),
    ("Haiti", "HT", "+509", "🇭🇹"),
    ("Honduras", "HN", "+504", "🇭🇳"),
    ("Hong Kong", "HK", "+852", "🇭🇰"),
    ("Hungary", "HU", "+36", "🇭🇺"),
    ("Iceland", "IS", "+354", "🇮🇸"),
    ("India", "IN", "+91", "🇮🇳"),
    ("Indonesia", "ID", "+62", "🇮🇩"),
    ("Iran", "IR", "+98", "🇮🇷"),
    ("Iraq", "IQ", "+964", "🇮🇶"),
    ("Ireland", "IE", "+353", "🇮🇪"),
    ("Isle of Man", "IM", "+44", "🇮🇲"),
    ("Israel", "IL", "+972", "🇮🇱"),
    ("Italy", "IT", "+39", "🇮🇹"),
    ("Jamaica", "JM", "+1876", "🇯🇲"),
    ("Japan", "JP", "+81", "🇯🇵"),
    ("Jersey", "JE", "+44", "🇯🇪"),
    ("Jordan", "JO", "+962", "🇯🇴"),
    ("Kazakhstan", "KZ", "+7", "🇰🇿"),
    ("Kenya", "KE", "+254", "🇰🇪"),
    ("Kiribati", "KI", "+686", "🇰🇮"),
    ("Kosovo", "XK", "+383", "🇽🇰"),
    ("Kuwait", "KW", "+965", "🇰🇼"),
    ("Kyrgyzstan", "KG", "+996", "🇰🇬"),
    ("Laos", "LA", "+856", "🇱🇦"),
    ("Latvia", "LV", "+371", "🇱🇻"),
    ("Lebanon", "LB", "+961", "🇱🇧"),
    ("Lesotho", "LS", "+266", "🇱🇸"),
    ("Liberia", "LR", "+231", "🇱🇷"),
    ("Libya", "LY", "+218", "🇱🇾"),
    ("Liechtenstein", "LI", "+423", "🇱🇮"),
    ("Lithuania", "LT", "+370", "🇱🇹"),
    ("Luxembourg", "LU", "+352", "🇱🇺"),
    ("Macao", "MO", "+853", "🇲🇴"),
    ("Madagascar", "MG", "+261", "🇲🇬"),
    ("Malawi", "MW", "+265", "🇲🇼"),
    ("Malaysia", "MY", "+60", "🇲🇾"),
    ("Maldives", "MV", "+960", "🇲🇻"),
    ("Mali", "ML", "+223", "🇲🇱"),
    ("Malta", "MT", "+356", "🇲🇹"),
    ("Marshall Islands", "MH", "+692", "🇲🇭"),
    ("Martinique", "MQ", "+596", "🇲🇶"),
    ("Mauritania", "MR", "+222", "🇲🇷"),
    ("Mauritius", "MU", "+230", "🇲🇺"),
    ("Mayotte", "YT", "+262", "🇾🇹"),
    ("Mexico", "MX", "+52", "🇲🇽"),
    ("Micronesia", "FM", "+691", "🇫🇲"),
    ("Moldova", "MD", "+373", "🇲🇩"),
    ("Monaco", "MC", "+377", "🇲🇨"),
    ("Mongolia", "MN", "+976", "🇲🇳"),
    ("Montenegro", "ME", "+382", "🇲🇪"),
    ("Montserrat", "MS", "+1664", "🇲🇸"),
    ("Morocco", "MA", "+212", "🇲🇦"),
    ("Mozambique", "MZ", "+258", "🇲🇿"),
    ("Myanmar", "MM", "+95", "🇲🇲"),
    ("Namibia", "NA", "+264", "🇳🇦"),
    ("Nauru", "NR", "+674", "🇳🇷"),
    ("Nepal", "NP", "+977", "🇳🇵"),
    ("Netherlands", "NL", "+31", "🇳🇱"),
    ("New Caledonia", "NC", "+687", "🇳🇨"),
    ("New Zealand", "NZ", "+64", "🇳🇿"),
    ("Nicaragua", "NI", "+505", "🇳🇮"),
    ("Niger", "NE", "+227", "🇳🇪"),
    ("Nigeria", "NG", "+234", "🇳🇬"),
    ("Niue", "NU", "+683", "🇳🇺"),
    ("Norfolk Island", "NF", "+672", "🇳🇫"),
    ("North Korea", "KP", "+850", "🇰🇵"),
    ("North Macedonia", "MK", "+389", "🇲🇰"),
    ("Northern Mariana Islands", "MP", "+1670", "🇲🇵"),
    ("Norway", "NO", "+47", "🇳🇴"),
    ("Oman", "OM", "+968", "🇴🇲"),
    ("Pakistan", "PK", "+92", "🇵🇰"),
    ("Palau", "PW", "+680", "🇵🇼"),
    ("Palestine", "PS", "+970", "🇵🇸"),
    ("Panama", "PA", "+507", "🇵🇦"),
    ("Papua New Guinea", "PG", "+675", "🇵🇬"),
    ("Paraguay", "PY", "+595", "🇵🇾"),
    ("Peru", "PE", "+51", "🇵🇪"),
    ("Philippines", "PH", "+63", "🇵🇭"),
    ("Poland", "PL", "+48", "🇵🇱"),
    ("Portugal", "PT", "+351", "🇵🇹"),
    ("Puerto Rico", "PR", "+1787", "🇵🇷"),
    ("Qatar", "QA", "+974", "🇶🇦"),
    ("Réunion", "RE", "+262", "🇷🇪"),
    ("Romania", "RO", "+40", "🇷🇴"),
    ("Russia", "RU", "+7", "🇷🇺"),
    ("Rwanda", "RW", "+250", "🇷🇼"),
    ("Saint Barthélemy", "BL", "+590", "🇧🇱"),
    ("Saint Helena", "SH", "+290", "🇸🇭"),
    ("Saint Kitts & Nevis", "KN", "+1869", "🇰🇳"),
    ("Saint Lucia", "LC", "+1758", "🇱🇨"),
    ("Saint Martin", "MF", "+590", "🇲🇫"),
    ("Saint Pierre & Miquelon", "PM", "+508", "🇵🇲"),
    ("Saint Vincent & the Grenadines", "VC", "+1784", "🇻🇨"),
    ("Samoa", "WS", "+685", "🇼🇸"),
    ("San Marino", "SM", "+378", "🇸🇲"),
    ("São Tomé & Príncipe", "ST", "+239", "🇸🇹"),
    ("Saudi Arabia", "SA", "+966", "🇸🇦"),
    ("Senegal", "SN", "+221", "🇸🇳"),
    ("Serbia", "RS", "+381", "🇷🇸"),
    ("Seychelles", "SC", "+248", "🇸🇨"),
    ("Sierra Leone", "SL", "+232", "🇸🇱"),
    ("Singapore", "SG", "+65", "🇸🇬"),
    ("Sint Maarten", "SX", "+1721", "🇸🇽"),
    ("Slovakia", "SK", "+421", "🇸🇰"),
    ("Slovenia", "SI", "+386", "🇸🇮"),
    ("Solomon Islands", "SB", "+677", "🇸🇧"),
    ("Somalia", "SO", "+252", "🇸🇴"),
    ("South Africa", "ZA", "+27", "🇿🇦"),
    ("South Korea", "KR", "+82", "🇰🇷"),
    ("South Sudan", "SS", "+211", "🇸🇸"),
    ("Spain", "ES", "+34", "🇪🇸"),
    ("Sri Lanka", "LK", "+94", "🇱🇰"),
    ("Sudan", "SD", "+249", "🇸🇩"),
    ("Suriname", "SR", "+597", "🇸🇷"),
    ("Svalbard & Jan Mayen", "SJ", "+47", "🇸🇯"),
    ("Sweden", "SE", "+46", "🇸🇪"),
    ("Switzerland", "CH", "+41", "🇨🇭"),
    ("Syria", "SY", "+963", "🇸🇾"),
    ("Taiwan", "TW", "+886", "🇹🇼"),
    ("Tajikistan", "TJ", "+992", "🇹🇯"),
    ("Tanzania", "TZ", "+255", "🇹🇿"),
    ("Thailand", "TH", "+66", "🇹🇭"),
    ("Timor-Leste", "TL", "+670", "🇹🇱"),
    ("Togo", "TG", "+228", "🇹🇬"),
    ("Tokelau", "TK", "+690", "🇹🇰"),
    ("Tonga", "TO", "+676", "🇹🇴"),
    ("Trinidad & Tobago", "TT", "+1868", "🇹🇹"),
    ("Tunisia", "TN", "+216", "🇹🇳"),
    ("Turkey", "TR", "+90", "🇹🇷"),
    ("Turkmenistan", "TM", "+993", "🇹🇲"),
    ("Turks & Caicos Islands", "TC", "+1649", "🇹🇨"),
    ("Tuvalu", "TV", "+688", "🇹🇻"),
    ("U.S. Virgin Islands", "VI", "+1340", "🇻🇮"),
    ("Uganda", "UG", "+256", "🇺🇬"),
    ("Ukraine", "UA", "+380", "🇺🇦"),
    ("United Arab Emirates", "AE", "+971", "🇦🇪"),
    ("United Kingdom", "GB", "+44", "🇬🇧"),
    ("United States", "US", "+1", "🇺🇸"),
    ("Uruguay", "UY", "+598", "🇺🇾"),
    ("Uzbekistan", "UZ", "+998", "🇺🇿"),
    ("Vanuatu", "VU", "+678", "🇻🇺"),
    ("Vatican City", "VA", "+39", "🇻🇦"),
    ("Venezuela", "VE", "+58", "🇻🇪"),
    ("Vietnam", "VN", "+84", "🇻🇳"),
    ("Wallis & Futuna", "WF", "+681", "🇼🇫"),
    ("Yemen", "YE", "+967", "🇾🇪"),
    ("Zambia", "ZM", "+260", "🇿🇲"),
    ("Zimbabwe", "ZW", "+263", "🇿🇼"),
];

impl AuthManager {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn set_credentials(&mut self, api_id: i32, api_hash: String) {
        self.api_id = api_id;
        self.api_hash = api_hash;
    }

    pub fn update_state(&mut self, state: AuthorizationState) {
        tracing::info!("Auth state transitioned to: {:?}", state);
        self.current_state = state;
    }

    pub fn get_all_countries() -> Vec<CountryInfo> {
        WORLD_COUNTRIES
            .iter()
            .map(|(name, code, calling, flag)| CountryInfo {
                name: (*name).to_string(),
                country_code: (*code).to_string(),
                calling_code: (*calling).to_string(),
                flag_emoji: (*flag).to_string(),
            })
            .collect()
    }

    pub fn get_common_countries() -> Vec<CountryInfo> {
        Self::get_all_countries()
    }

    pub fn match_country_by_dial_code(dial: &str) -> Option<CountryInfo> {
        let normalized = if dial.starts_with('+') {
            dial.to_string()
        } else {
            format!("+{}", dial.trim())
        };
        // Prefer primary country for shared codes (+1 -> US, +7 -> Russia, +44 -> UK, +39 -> Italy, +61 -> Australia)
        match normalized.as_str() {
            "+1" => Self::get_all_countries().into_iter().find(|c| c.country_code == "US"),
            "+7" => Self::get_all_countries().into_iter().find(|c| c.country_code == "RU"),
            "+44" => Self::get_all_countries().into_iter().find(|c| c.country_code == "GB"),
            "+39" => Self::get_all_countries().into_iter().find(|c| c.country_code == "IT"),
            "+61" => Self::get_all_countries().into_iter().find(|c| c.country_code == "AU"),
            "+358" => Self::get_all_countries().into_iter().find(|c| c.country_code == "FI"),
            "+47" => Self::get_all_countries().into_iter().find(|c| c.country_code == "NO"),
            "+590" => Self::get_all_countries().into_iter().find(|c| c.country_code == "GP"),
            "+599" => Self::get_all_countries().into_iter().find(|c| c.country_code == "CW"),
            "+262" => Self::get_all_countries().into_iter().find(|c| c.country_code == "RE"),
            _ => Self::get_all_countries()
                .into_iter()
                .find(|c| c.calling_code == normalized),
        }
    }

    pub fn switch_account(&mut self, account_id: u32) -> Result<()> {
        if self.accounts.iter().any(|acc| acc.account_id == account_id) {
            for acc in &mut self.accounts {
                acc.is_active = acc.account_id == account_id;
            }
            self.active_account_id = account_id;
            Ok(())
        } else {
            Err(RustGramError::Auth(format!("Account {} not found", account_id)))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_auth_manager_state_transition() {
        let mut auth = AuthManager::new();
        assert_eq!(auth.current_state, AuthorizationState::WaitTdlibParameters);

        auth.update_state(AuthorizationState::WaitPhoneNumber);
        assert_eq!(auth.current_state, AuthorizationState::WaitPhoneNumber);

        auth.update_state(AuthorizationState::WaitCode {
            phone_number: "+123456789".into(),
            code_length: 5,
            send_type: "telegram_message".into(),
            timeout_sec: 60,
        });

        match &auth.current_state {
            AuthorizationState::WaitCode { code_length, .. } => {
                assert_eq!(*code_length, 5);
            }
            _ => panic!("Expected WaitCode"),
        }
    }

    #[test]
    fn test_countries_list() {
        let countries = AuthManager::get_all_countries();
        assert!(countries.len() >= 230, "Expected full world directory, got {}", countries.len());
        assert!(countries.iter().any(|c| c.calling_code == "+1" && c.country_code == "US"));
        assert!(countries.iter().any(|c| c.calling_code == "+7" && c.country_code == "RU"));
        assert!(countries.iter().any(|c| c.calling_code == "+380" && c.country_code == "UA"));
        assert!(countries.iter().any(|c| c.calling_code == "+888" && c.country_code == "FT"));
        assert!(countries.iter().any(|c| c.calling_code == "+81" && c.country_code == "JP"));

        let matched = AuthManager::match_country_by_dial_code("7").unwrap();
        assert_eq!(matched.country_code, "RU");
    }
}
