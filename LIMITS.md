# PyroMinions 1.1.0 — Minyon limitleri

`config.yml` içinde iki bağımsız sınır bulunur:

```yaml
max-per-player: 20
max-per-island: 20
```

Kişisel sınır oyuncunun bütün adalardaki kayıtlı minyonlarının toplamıdır. Ada sınırı SuperiorSkyblock2 ada UUID'sine göre bütün oyuncuların minyonlarının toplamıdır. Madenci, balıkçı ve besleyici birlikte sayılır; çevrimdışı sahiplerin ve yüklenmemiş chunk'lardaki minyonların kayıtları da sayılır. SuperiorSkyblock2 olmadan yalnızca kişisel sınır uygulanır.

`minion.max.<sayı>` kişisel varsayılanın yerine geçer. `minion.max.3` tam 3 hak verir; varsayılan 20 olsa da 3 uygulanır. Birden fazla verilmiş sayısal izin varsa en yüksek sayı seçilir. Reddedilmiş izinler, `minion.max.*`, negatif veya geçersiz sayılar limit vermez. OP/admin olmak tek başına limiti aşma hakkı vermez. İzin değişiklikleri sonraki yerleştirmede hemen dikkate alınır.

LuckPerms kullanılıyorsa örnekler:

```text
/lp group default permission set minion.max.3 true
/lp group vip permission set minion.max.10 true
/lp user Oyuncu permission set minion.max.25 true
```

İzinle kişisel sınır artırılsa da adanın toplam sınırı aşılmaz. Örneğin ada limiti 20 ise `minion.max.25` olan oyuncu da adaya toplam 20'nin üzerinde minyon ekleyemez. Ada limiti bu sürümde ortak config ayarıdır; ada seviyesine veya bir üyenin iznine göre değişmez.

Her iki ayarda da `0` yeni yerleştirmeyi engeller. Negatif ve tam sayı olmayan config değerleri reddedilir. Limit düşürüldüğünde mevcut minyonlar silinmez veya durdurulmaz; yalnızca yeni yerleştirme engellenir. Minyon kaldırmak bir yer açar.

Eski config dosyasına `max-per-island: 20` otomatik eklenir ve `config-before-limits.yml.bak` yedeği alınır. Kişisel mevcut config ayarı korunur. Eski dil dosyalarında yeni limit mesajları JAR varsayılanlarından okunur; özelleştirmek için `lang.yml` içindeki `messages.player-limit` ve `messages.island-limit` alanlarını kullanın (`%limit%` desteklenir).

Plugin sürümü ve JAR adı 1.1.0 olarak korunur.


[README](README.md)
