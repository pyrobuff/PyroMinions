# Sunucu uyumluluğu

PyroMinions `1.0.0`, **Paper 1.21–1.21.11** serisini ve **Java 21** çalışma hedefini kullanır. Derleme en eski Paper `1.21` API'siyle yapılır; `plugin.yml` içindeki `api-version` değeri de `1.21` olarak tanımlıdır.

## Sürüm kapsamı

Yayımlanmış Paper sürümlerinde sınanan aralık:

`1.21`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.6`, `1.21.7`, `1.21.8`, `1.21.9`, `1.21.10`, `1.21.11`.

- Paper **1.21.2** için resmi build bulunmadığından bu sürümde çalışma zamanı kontrolü yapılmamıştır.
- **1.21.5** ve **1.21.9** için kontroller, resmi tarihsel **ALPHA** build'leriyle yapılmıştır; bu sunucu build'lerinin kararlılığı için garanti verilmez.
- Spigot ve Folia desteği sunulmaz. 1.21.11 sonrasındaki sürümler bu kapsamda değildir.

## Bağımlılıklar

Vault veya VaultUnlocked ile bir Vault uyumlu ekonomi sağlayıcısı zorunludur. SuperiorSkyblock2, varsayılan ada şartı açıkken gereklidir.

Her bağımlılığın seçilen Minecraft sürümünü ayrıca desteklemesi gerekir. Bir sunucu sürümünde PyroMinions'ın çalışması, bütün bağımlılık sürümlerinin aynı ortamla uyumlu olduğu anlamına gelmez.

## Kontrollerin sınırları

İzole sunucu kontrolleri kontrollü Player/Economy vekilleri kullanır. Gerçek SuperiorSkyblock ada kurulumu ve 1.000 aktif minyonla MSPT ölçümü bu kontrollerin kapsamında değildir. Canlı sunucunun diğer pluginleri ve donanımıyla ayrıca değerlendirme yapılmalıdır.

Geliştirme gereksinimleri: [DEVELOPMENT.md](DEVELOPMENT.md).
