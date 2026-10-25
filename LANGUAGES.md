# Dil seçimi / Language selection

PyroMinions, sunucu genelinde Türkçe ve İngilizce destekler. Varsayılan dil Türkçedir; sürüm `1.1.0` olarak korunur.

## Türkçe

`plugins/PyroMinions/config.yml` içinde:

```yaml
language: tr
```

İngilizce için değeri `en` yapıp `/pma reload` kullanın. Dil desteğini içeren JAR ilk kez kurulurken sunucuyu tamamen yeniden başlatın.

| Dosya | Görev |
|---|---|
| `lang.yml` | Türkçe mesajlar, yardım ve ortak etiketler. |
| `lang_en.yml` | İngilizce mesajlar, yardım, minyon/skin adları ve menü metinleri. |
| `menus/*.yml` | Her iki dil için ortak menü konumu, boyutu, materyali ve işlemleri; Türkçe menü metinleri. |

İngilizce menü metinleri `lang_en.yml` içindeki `menus` bölümünden uygulanır. Dil değiştirmek menü dosyalarını, sayısal ayarları, minyon kayıtlarını veya oyuncuların özel minyon isimlerini yeniden yazmaz. Kaldırılmış düğmeler dil değişiminde geri eklenmez.

Varsayılan minyon isimleri ve seviye ekleri seçilen dilde gösterilir. İngilizce adları `labels.minion-types`, skin adlarını `labels.skin-names` üzerinden düzenleyebilirsiniz. Özel bir `hologram-format` metni kullanıyorsanız metnini istediğiniz dilde ayrıca düzenleyin; varsayılan hologram biçimi otomatik çevrilir.

Metinlerde `%feed_price%`, `%level%` gibi yer tutucuları koruyun. Fiyatların rengi YAML şablonundaki renk koduyla belirlenir. Eksik bir çeviri anahtarı, seçilen dilin JAR içindeki varsayılanından tamamlanır.

`tr` ve `en` dışındaki değerler reddedilir; başarısız yenilemede önceki çalışan ayarlar korunur. Dil sunucu genelindedir, oyuncu başına otomatik seçim yapılmaz.

## English

Set the following in `plugins/PyroMinions/config.yml`:

```yaml
language: en
```

Run `/pma reload` to apply the change. Restart the server when installing the updated JAR for the first time.

Edit `lang_en.yml` to customize English messages, command help, default names and menu text. The nested `menus` section translates existing menu elements; keep layout, materials and actions in `menus/*.yml`. The `labels.minion-types` and `labels.skin-names` sections define translated catalog names.

The language setting applies to the whole server. Custom minion names and stored records are preserved. Default hologram text is translated automatically; translate any custom `hologram-format` yourself. Keep placeholders intact. Missing entries fall back to the bundled English defaults.

To return to Turkish, set `language: tr` and reload. Turkish messages remain in `lang.yml`; the original Turkish menu text remains in `menus/*.yml`.
