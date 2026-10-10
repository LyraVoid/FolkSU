use anyhow::{Result, bail};
use serde::{Deserialize, Serialize};

pub const MAX_CONFIG: usize = 128 * 1024;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Config {
    pub schema: u32,
    pub automatic: bool,
    pub settings: Settings,
    pub rules: Vec<Rule>,
    pub lkm: Lkm,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            schema: 1,
            automatic: false,
            settings: Settings::default(),
            rules: Vec::new(),
            lkm: Lkm::default(),
        }
    }
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Settings {
    pub hide_mounts: Option<bool>,
    pub logging: Option<bool>,
    pub avc_spoof: Option<bool>,
    pub uname: Option<Uname>,
    pub cmdline: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Uname {
    pub release: String,
    pub version: String,
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Lkm {
    pub hidden_modules: Vec<String>,
    pub mount_prefixes: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "snake_case", deny_unknown_fields)]
pub enum Rule {
    Path {
        path: String,
        looping: bool,
    },
    Map {
        path: String,
    },
    Redirect {
        path: String,
        destination: String,
        uid_scheme: u32,
    },
    Kstat {
        path: String,
        full_clone: bool,
    },
}

pub fn path(value: &str) -> Result<()> {
    if !value.starts_with('/')
        || value == "/"
        || value.len() >= 256
        || value.chars().any(char::is_whitespace)
        || value.chars().any(char::is_control)
        || value.split('/').any(|p| matches!(p, "." | ".."))
        || value.contains("//")
    {
        bail!("Invalid absolute SUSFS path");
    }
    Ok(())
}

fn text(value: &str, max: usize) -> Result<()> {
    if value.len() >= max || value.chars().any(char::is_control) {
        bail!("Invalid SUSFS text field");
    }
    Ok(())
}

impl Config {
    pub fn parse(input: &str) -> Result<Self> {
        if input.len() > MAX_CONFIG {
            bail!("SUSFS config is too large");
        }
        let config: Self = serde_json::from_str(input)?;
        config.validate()?;
        Ok(config)
    }

    pub fn validate(&self) -> Result<()> {
        if self.schema != 1
            || self.rules.len() > 128
            || self.lkm.hidden_modules.len() > 16
            || self.lkm.mount_prefixes.len() > 8
        {
            bail!("Unsupported schema or too many rules");
        }
        if let Some(uname) = &self.settings.uname {
            text(&uname.release, 65)?;
            text(&uname.version, 65)?;
        }
        if let Some(cmdline) = &self.settings.cmdline {
            text(cmdline, 8192)?;
        }
        let mut identities = std::collections::HashSet::new();
        for rule in &self.rules {
            let (kind, target) = match rule {
                Rule::Path { path, .. } => ("path", path),
                Rule::Map { path } => ("map", path),
                Rule::Kstat { path, .. } => ("kstat", path),
                Rule::Redirect {
                    path,
                    destination,
                    uid_scheme,
                } => {
                    crate::susfs::model::path(destination)?;
                    if *uid_scheme > 4 || path == destination {
                        bail!("Invalid redirect");
                    }
                    ("redirect", path)
                }
            };
            path(target)?;
            if !identities.insert((kind, target)) {
                bail!("Duplicate SUSFS rule");
            }
        }
        for name in &self.lkm.hidden_modules {
            if name.is_empty()
                || name.len() > 63
                || !name.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_')
            {
                bail!("Invalid module name");
            }
            if !identities.insert(("module", name)) {
                bail!("Duplicate hidden module");
            }
        }
        for prefix in &self.lkm.mount_prefixes {
            path(prefix)?;
            if !identities.insert(("prefix", prefix)) {
                bail!("Duplicate hidden mount prefix");
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn default_is_opt_in() {
        let config = Config::default();
        assert!(!config.automatic);
        assert!(Config::parse(&serde_json::to_string(&config).unwrap()).is_ok());
    }
    #[test]
    fn strict_import() {
        let original = serde_json::to_value(Config::default()).unwrap();
        let mut unknown = original.clone();
        unknown["execute"] = true.into();
        assert!(Config::parse(&unknown.to_string()).is_err());
        let mut schema = original;
        schema["schema"] = 2.into();
        assert!(Config::parse(&schema.to_string()).is_err());
        assert!(Config::parse(&" ".repeat(MAX_CONFIG + 1)).is_err());
        for invalid in [
            "relative",
            "/",
            "/data/../x",
            "/data/a\nb",
            "/data//a",
            "/data/a b",
        ] {
            assert!(path(invalid).is_err());
        }
    }

    #[test]
    fn rejects_duplicate_rules_and_extensions() {
        let mut config = Config::default();
        config.rules = vec![
            Rule::Map {
                path: "/data/test".to_owned()
            };
            2
        ];
        assert!(config.validate().is_err());
        config.rules.clear();
        config.lkm.hidden_modules = vec!["susfs".to_owned(); 2];
        assert!(config.validate().is_err());
        config.lkm.hidden_modules.clear();
        config.lkm.mount_prefixes = vec!["/data/test".to_owned(); 2];
        assert!(config.validate().is_err());
    }
}
