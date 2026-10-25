# PyroMinions

**Paper, Purpur ve Spigot için özelleştirilebilir madenci, balıkçı ve besleyici minyonları.**

PyroMinions; üretim, besleme ve minyon yönetimini oyun içi menülerle sunar. SuperiorSkyblock2 entegrasyonu, kişi ve ada başına limitler, permission ile belirlenen minyon hakları ve yapılandırılabilir görünüşler içerir.

**Sürüm:** `1.1.0` · **Java:** `21` (1.21.x) / `25` (26.x) · **Yapımcı:** `pyroBuff`

[Kurulum](#kurulum) · [Komutlar](#komutlar) · [Limitler](LIMITS.md) · [Yapılandırma](#yapılandırma) · [Uyumluluk](COMPATIBILITY.md)

## Özellikler

- **Üç minyon türü:** blok kıran madenci, balık üreten balıkçı ve diğer minyonlara can aktaran besleyici.
- **Oyun içi yönetim:** yön değiştirme, besleme, yükseltme, sandık bağlama, isim, skin ve hologram ayarları.
- **Minyon marketi:** tür bazında satış durumu, başlangıç seviyesi ve fiyat ayarları.
- **SuperiorSkyblock2 entegrasyonu:** ada sahibi/üye kontrolü, ada alanı denetimi ve ada silindiğinde minyonların temizlenmesi.
- **İki ayrı limit:** oyuncunun kişisel hakkı ve ada üyelerinin ortak toplam sınırı.
- **18 hazır görünüş:** dokulu kafalar ve ayrı ayarlanabilen deri kıyafet renkleri; kaynak paketi gerekmez.
- **Kalıcı istatistikler:** kırılan blok, üretilen balık ve başarılı besleme sayaçları.
- **İki dil seçeneği:** Türkçe ve İngilizce mesajlar, menüler ve komut yardımı. [Dil seçimi](LANGUAGES.md).
- **Özelleştirilebilir YAML dosyaları:** açıklamalı ayarlar, renkli mesajlar ve düzenlenebilir menüler.
- **Geliştirici API:** balıkçılık ganimetini düzenlemek için özel olay desteği.


## Gereksinimler

| Bileşen | Kullanım |
|---|---|
| **Paper / Purpur / Spigot** | Minecraft `1.21.x`, `26.1.x` ve `26.2.x`; test edilen sürümler [uyumluluk belgesinde](COMPATIBILITY.md). |
| **Java** | `1.21.x` sunucularında Java **21**; `26.1.x` ve `26.2.x` sunucularında Java **25**. |
| **Vault veya VaultUnlocked** | Zorunlu ekonomi köprüsü. |
| **Vault uyumlu ekonomi sağlayıcısı** | Market, yükseltme ve parayla besleme; örneğin EssentialsX. |
| **SuperiorSkyblock2** | Varsayılan `require-island: true` ayarında zorunlu. |

SuperiorSkyblock2 olmadan kullanmak için `config.yml` içindeki `require-island` değerini `false` yapın. Bu durumda ada başına limit uygulanmaz. Bağımlılıkların da kullandığınız Minecraft sürümünü desteklemesi gerekir.

## Kurulum

1. GitHub **Releases** bölümünden `PyroMinions-1.1.0.jar` dosyasını indirin.
2. Sunucuyu durdurun ve gerekli bağımlılıkları kurun.
3. JAR dosyasını sunucunun `plugins/` klasörüne koyun.
4. Sunucuyu başlatın; `plugins/PyroMinions/` altında ayarlar oluşur.
5. Ayarları düzenleyin ve `/pma reload` ile yenileyin.

Güncellerken sunucuyu durdurup mevcut JAR'ı değiştirin; aynı pluginin iki JAR'ını birlikte bırakmayın. Plugin güncellemelerinde tam yeniden başlatma kullanın. YAML değişiklikleri `/pma reload` ile uygulanır; otomatik kayıt aralığı değişikliği yeniden başlatma gerektirir.

## Dil seçimi

`config.yml` içindeki `language: tr` Türkçe, `language: en` İngilizce seçer. Değişikliği `/pma reload` ile uygulayın. Ayrıntılar: [LANGUAGES.md](LANGUAGES.md).

## Nasıl kullanılır?

1. `/pm shop` ile marketi açıp bir minyon alın.
2. Minyon eşyasını ana elde tutarak sağlam bir bloğun üstüne sağ tıklayın.
3. Minyona sağ tıklayıp yönetim menüsünü açın.
4. Madenciyi kırılacak bloklara, balıkçıyı önünde veya ön-altında su olacak şekilde yönlendirin.
5. Ürünleri depolamak için menüden **Sandığa bağla** seçeneğini kullanıp 30 saniye içinde sandığa sağ tıklayın.

**Besleme:** Ana elde pişmiş inek eti (`COOKED_BEEF`) ile sağ tıklamak bir et tüketerek varsayılan 50 can doldurur. Canı tamsa et harcanmaz. Menüden para karşılığında tamamen doldurmak da mümkündür.

**Kaldırma ve sahiplik:** Yalnızca sahibi, canı tamamen dolu minyonu sol tıklayarak envanterine alabilir. Eşya başka oyuncuya verilebilir; yeniden yerleştiren oyuncu yeni sahibi olur. İstatistikler korunur.

**Çalışma şartları:** Sahibi çevrimiçi, minyonun chunk'ı yüklü ve ada erişimi geçerli olmalıdır. Sahibin aynı dünyada veya adada bulunması gerekmez. Plugin chunk'ları zorla yüklemez. İlk çalışma, yükü dağıtmak için bir tam işlem aralığı ile yaklaşık iki aralık arasında başlar.

| Tür | Çalışma biçimi |
|---|---|
| **Madenci** | Baktığı yönde aynı yükseklikteki uygun blokları kırar. İleri erişim seviyeye göre ayarlanır; boşta can harcamaz ve envanterli blokları kırmaz. |
| **Balıkçı** | Önündeki veya ön-altındaki sudan yapılandırılmış ağırlıklara göre ürün üretir. |
| **Besleyici** | Menzilindeki aynı sahibin madenci ve balıkçılarına kendi canından aktarır. Diğer besleyicileri doldurmaz; başarılı tur ayrıca 1 can harcar. |

Bağlı sandık yoksa, doluysa veya erişilemiyorsa fazla ürünler yere düşer. Erişilebilir çift sandıklar ortak 54 slotla kullanılır.

## Komutlar

### Oyuncu

| Komut | Açıklama |
|---|---|
| `/pm` veya `/pm list` | Kendi minyonlarını listeler. |
| `/pm shop` | Minyon marketini açar. |
| `/pm help` | Renkli komut yardımını gösterir. |

Alternatif komut adları: `/pyrominions`, `/pminions`, `/pyrominion`.

### Yönetici

| Komut | Açıklama |
|---|---|
| `/pma get <tür> [seviye] [adet]` | Kendi envanterine minyon verir. |
| `/pma give <tür> <oyuncu> [seviye] [adet]` | Çevrimiçi oyuncuya minyon verir. |
| `/pma minions [oyuncu]` | Kendi veya belirtilen oyuncunun minyon listesini açar. |
| `/pma info <uuid>` | Minyonun kayıt bilgilerini gösterir. |
| `/pma remove [uuid]` | UUID ile veya en fazla 5 blok uzaktan bakılan minyonu **eşya iadesiz siler**. |
| `/pma reload` | Ayarları, mesajları ve menüleri yeniler. |
| `/pma stats` | Kayıtlı ve chunk'ı yüklü minyon sayılarını gösterir. |
| `/pma help` | Yönetici komutlarını ve kullanım örneklerini gösterir. |

`<...>` zorunlu, `[...]` isteğe bağlı parametredir. Varsayılan seviye ve adet `1`, verilebilen adet `1–64` aralığındadır. Türler: `miner` / `madenci`, `fisher` / `balikci`, `feeder` / `besleyici`.

```text
/pma get miner 1 3
/pma give fisher Oyuncu 2 1
```

Yönetici komutları yardımda ve Tab önerilerinde yalnızca yönetici izni olanlara gösterilir. Alternatif yönetici komutu: `/pyrominionsadmin`.

## İzinler ve limitler

| İzin | İşlev | Varsayılan |
|---|---|---|
| `pyrominions.use` | Oyuncu komutlarına ve minyon yerleştirmeye erişim. | Herkes |
| `pyrominions.shop` | Minyon marketinden alışveriş. | Herkes |
| `pyrominions.admin` | Yönetici komutları. | OP |
| `pyrominions.admin.remove` | Kalıcı silme; admin izninin alt iznidir. | OP |
| `minion.max.<sayı>` | Kişisel varsayılan yerine uygulanacak minyon hakkı. | Açıkça verilir |

```yaml
max-per-player: 20
max-per-island: 20
```

`minion.max.3` oyuncuya **tam 3 hak** verir. Birden fazla sayısal izin varsa en yüksek değer kullanılır. Kişisel izin ada toplam sınırını aşamaz; tüm minyon türleri birlikte sayılır. Limit düşürülürse mevcut minyonlar korunur, yeni yerleştirmeler engellenir.

LuckPerms örneği:

```text
/lp group default permission set minion.max.3 true
/lp group vip permission set minion.max.10 true
```

Ayrıntılar ve sıfır limit davranışı: [LIMITS.md](LIMITS.md).

## Yapılandırma

Dosyalar sunucuda `plugins/PyroMinions/`, kaynak kodda `src/main/resources/` altındadır. YAML dosyalarındaki `#` açıklamaları ayarların ne işe yaradığını anlatır.

| Dosya | İçerik |
|---|---|
| `config.yml` | Kişi/ada limitleri, ada şartı, kayıt, besleme, sandık mesafesi ve hologram. |
| `minions/miner.yml` | Madenci seviyeleri, ileri erişim, kırılabilir bloklar ve market ayarları. |
| `minions/fisher.yml` | Balıkçı seviyeleri, ganimet ağırlıkları ve market ayarları. |
| `minions/feeder.yml` | Besleyici seviyeleri, menzil, can aktarımı ve market ayarları. |
| `skins.yml` | Kafa dokuları, görünüş adları ve kıyafet renkleri. |
| `lang.yml` | Türkçe mesajlar, yardım, önek ve ortak etiketler. |
| `lang_en.yml` | İngilizce mesajlar, yardım, adlar ve menü metinleri. |
| `menus/*.yml` | Menü başlıkları, boyutlar, slotlar ve eşya açıklamaları. |

Metinlerde `&` renk kodları desteklenir. Menü slotları `0` ile başlar; boyutlar `9–54` arasında, 9'un katı olmalıdır. Mevcut `%yer_tutucular%` değerlerini koruyun. Skin kafa dokularının kaynakları [SKIN-SOURCES.md](SKIN-SOURCES.md) dosyasındadır.

`minions.yml` ve `transactions/` oyuncu verisidir. Sunucuyu durdurarak tutarlı yedek alın; bekleyen işlem günlüklerini elle silmeyin. Ani kapanışta son otomatik kayıttan sonraki ilerleme kaybolabilir.

## Kaynak kod

Plugin kaynakları ve varsayılan YAML dosyaları `src/main/` altındadır. Java 21 derleme hedefi ve Spigot 1.21 ortak API'si kullanılır; Paper olayları ayrı bir uyumluluk sınıfındadır.

API bağımlılıkları ve paketleme bilgileri: [DEVELOPMENT.md](DEVELOPMENT.md). Sunucu sürümü desteği: [COMPATIBILITY.md](COMPATIBILITY.md). Hazır JAR, GitHub Releases bölümünden edinilebilir.
