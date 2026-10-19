# Geliştirme

## Kaynak yapısı

- `src/main/java/`: plugin kaynak kodu.
- `src/main/resources/`: plugin tanımı ve varsayılan YAML dosyaları.

## Derleme gereksinimleri

JDK 21 kullanın ve Java derleme hedefini `--release 21` olarak ayarlayın. En eski desteklenen Paper 1.21 API'siyle derleyin.

| API | Projenin kullandığı sürüm |
|---|---|
| Paper | `1.21-R0.1-SNAPSHOT` — dosya adı `paper-api-1.21-R0.1-SNAPSHOT.jar` olmalıdır. |
| VaultUnlocked | `2.20.3` |
| SuperiorSkyblock2 | `2026.3` |
| Adventure API ve legacy/plain serializer | `4.26.1` |
| SnakeYAML | `2.2` |

API'lerin geçişli bağımlılıkları da gerekir; örneğin Guava, Gson, examination ve BungeeCord chat API'si. Bağımlılıkları ilgili projelerin resmi dağıtımından veya Maven deposundan edinin. Yerel `lib/` dosyaları kaynak deposuna dahil değildir.

## Paketleme

`src/main/java/` kaynaklarını gerekli API'lerle derleyin. `src/main/resources/` içeriğini JAR köküne ekleyin; `plugin.yml` JAR kökünde bulunmalıdır. Üçüncü taraf API JAR'larını pluginin içine paketlemeyin.

Çıktı adı `PyroMinions-1.0.0.jar` olmalıdır. Derleme ve test yardımcıları yerel geliştirme ortamında tutulur; GitHub deposunda paylaşılmaz.
