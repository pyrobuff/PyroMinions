# Sunucu uyumluluğu

PyroMinions `1.1.0`; **Paper, Purpur ve Spigot** üzerinde Minecraft **1.21.x**, **26.1.x** ve **26.2.x** sürüm ailelerini hedefler. Tek JAR kullanılır.

## Java gereksinimi

| Minecraft | Sunucu için Java |
|---|---|
| `1.21.x` | **21** |
| `26.1.x` / `26.2.x` | **25** |

JAR, Java 21 bytecode hedefiyle derlenir ve Java 25 üzerinde de çalışır. `api-version: '1.21'` en düşük Bukkit API gereksinimidir; desteklenen en yüksek Minecraft sürümü anlamına gelmez. Ortak kod Spigot 1.21 API'siyle derlenir; Paper'ın sohbet ve saldırı olayları yalnızca Paper/Purpur üzerinde yüklenen ayrı bir sınıftadır. Spigot'ta Bukkit sohbet ve kol sallama olayları kullanılır.

26.x oyuncu kayıtları `players/data/` altında, 1.21.x kayıtları `playerdata/` altında işlenir. Bu ayrım minyon yerleştirme ve geri alma işlemlerinin kayıt güvenliği için uygulanır.

## Çalışma zamanı kontrolleri

| Sunucu | Sürüm / build | Sonuç |
|---|---|---|
| Paper | `1.21.11` / `132` | 243 kontrol geçti, Java 21. |
| Paper | `26.1.2` / `74` | 243 kontrol geçti, Java 25. |
| Paper | `26.2` / `134` | 243 kontrol geçti, Java 25. |
| Purpur | `26.1.2` / `2592` | 243 kontrol geçti, Java 25. |
| Purpur | `26.2` / `2633` | 243 kontrol geçti, Java 25. |
| Spigot | `26.2` / resmi BuildTools | 243 kontrol geçti, Java 25; Bukkit kol sallama olayıyla toplama. |

Spigot 1.21 API'siyle, Paper ve Adventure kütüphaneleri olmadan 224 kural/API kontrolü de geçmiştir.

Önceki sürüm kontrollerinde Paper `1.21`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.6`, `1.21.7`, `1.21.8`, `1.21.9`, `1.21.10` ve `1.21.11` sınanmıştır. Paper `1.21.2` için resmi build bulunmamıştır; `1.21.5` ve `1.21.9` tarihsel ALPHA build'leriyle sınanmıştır.

Tablo fiilen çalıştırılan sürümleri gösterir. Yayımlanmamış yama sürümleri ve her sunucu ailesinin her eski sürümü için ayrı test sonucu değildir. Folia desteği yoktur.

## Bağımlılıklar

Vault veya VaultUnlocked ile Vault uyumlu bir ekonomi sağlayıcısı gerekir. Varsayılan `require-island: true` ayarında SuperiorSkyblock2 de gereklidir. Ada entegrasyonu kullanılmayacaksa bu ayarı `false` yapın.

Her bağımlılığın seçilen sunucu ve Minecraft sürümünü ayrıca desteklemesi gerekir.

## Kontrollerin kapsamı

İzole sunucu kontrolleri kontrollü Player/Economy vekilleri kullanır. Menü, dil geçişi, üretim, besleme, kayıt, minyon yerleştirme/geri alma ve hata kurtarma davranışlarını sınar. Gerçek istemci bağlantısı, SuperiorSkyblock ada kurulumu ve 1.000 aktif minyonla MSPT ölçümü bu kontrollerin kapsamında değildir.

Geliştirme gereksinimleri: [DEVELOPMENT.md](DEVELOPMENT.md).
