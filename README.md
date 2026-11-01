# HomeDesigner – belsőépítészeti 3D tervező

Lakás vagy ház alaprajzát rajzolhatod meg vele, falakkal, nyílászárókkal, padló- és falburkolattal és tetővel. Bútorokat importálhatsz 3D modellfájlokból, a tervet valós időben körbeforgathatod 3D-ben, és fotórealisztikus képet is renderelhetsz róla.

## Indítás

- **EXE:** `dist\HomeDesigner\HomeDesigner.exe`. A teljes `dist\HomeDesigner` mappa önállóan hordozható, Java telepítése nem kell hozzá. A mellette lévő `objects` mappa a bútorkatalógus.
- **Fejlesztőként:** `mvn package`, majd `java -jar target\dist\homedesigner.jar`, vagy futtasd az IDE-ből a `hu.helmetsoft.homedesigner.Launcher` osztályt.
- **EXE újraépítése:** `powershell -ExecutionPolicy Bypass -File build-exe.ps1`

Indításkor egy saját tervezésű, építészeti illusztrációs nyitókép jelenik meg **5 másodpercig**, „Home Designer” és „created by helmetsoft.com” felirattal.

## Nyelv és katalógussorrend

A jobb felső sarokban a rajzolt **magyar zászló / Magyar** és **brit zászló / English** gombokkal válthatsz. A kezelőfelület, súgó, anyag- és modellnevek, tulajdonságok, hibaüzenetek és rendervezérlők **azonnal** átváltanak; nem kell újraindítani. A választás a következő indításra is megmarad. A megnyitott terv, fájlnév, saját elnevezések, kijelölés, mentetlen módosítások, visszavonási előzmények, kamera és alaprajzi nézet megmaradnak. Folyamatban lévő videófelvételnél előbb állítsd le és várd meg a mentést.

A katalógus **a megjelenített nevek alapján, az aktív nyelv ábécérendjében** rendez, a „Minden modell” nézetben is, nem kategóriánként csoportosítva. A kategóriaválasztó rendes kategóriái szintén ábécérendesek; a „Minden modell” és a fürdőszobai gyűjtőszűrő elöl maradnak. A kereső a lefordított nevet és az eredeti fájlnevet is megtalálja, ékezet nélkül is. A fájlok nevét és a saját importált objektumok elnevezését a nyelvváltás nem írja át.

## Kezelés röviden

1. **Fal:** kattints az alaprajzon a kezdőpontra, majd minden sarokpontra. Dupla kattintás vagy Esc zárja le a rajzolást. A pontok a rácshoz és a meglévő falakhoz illeszkednek.
2. **Helyiség:** egy téglalap húzásával a falakat és a padlót egyszerre hozod létre. **Padló (sokszög):** szabálytalan alakú padlót rajzolhatsz vele.
3. **Ajtó / Ablak:** kattints egy falra. A nyílászáró utána a fal mentén húzható.
4. **Bútor:** húzd át a bal oldali katalógusból az alaprajzra vagy a 3D nézetbe, vagy kattints rá duplán. Az **Importálás...** gombbal saját fájlt, a **Mappa...** gombbal egész mappát adhatsz a katalógushoz.
5. **Szerkesztés:** kattints egy elemre. A jobb oldali panelen centiméterben állíthatod a méreteit, a helyzetét, a burkolatát (anyag, szín, mintaméret, saját kép) és a forgatását. A bútor sarkainál húzva átméretezhető, a kör alakú fogantyúval forgatható.
6. **Tető / erkély:** jelöld ki a kívánt épület falát vagy helyiségét, majd válaszd az **Új tető a falakra** parancsot. A tető az egyenes és íves falak külső körvonalához illeszkedik. Nyereg-, kontyolt, félnyereg- és lapos tető mellett kör alapra kúp és kupola is választható. Az ereszmagasság, a túlnyúlás és a fedés is állítható. Ugyanitt adhatók hozzá tetőablakok, tetőteraszok és homlokzati erkélyek.
7. **Méretek:** a „Méretek” kapcsolóval megjeleníthetők a fal- és bútorméretek. A Méretvonal eszközzel saját méretvonal is rajzolható.
8. **3D nézet:**
   - bal egérgomb: forgatás;
   - jobb egérgomb: mozgatás;
   - görgő: nagyítás;
   - „Séta” mód: mozgás W/A/S/D gombokkal; helyben fordulás a ←/→ nyilakkal vagy egérhúzással, mint egy FPS játékban (← / balra húzás = balra nézel);
   - a bútorok a 3D nézetben is húzhatók.
9. **Fotó render (F9):** fotórealisztikus kép a 3D nézet kameraállásából, globális megvilágítással, napfénnyel, árnyékokkal és zajszűréssel. A kész kép PNG vagy JPG formátumba menthető.
10. **Mentés:** a terv `.hdp` fájlba menthető. Visszavonás: Ctrl+Z, újra: Ctrl+Y, másolat: Ctrl+D, törlés: Del.

## Pince, földszint és emeletek

A felső **Épületszint** választóval válthatsz a szerkesztett szintek között. A **+ Szint** üres szintet készít; a **Szint szerkesztése** paranccsal nevezd el és add meg az abszolút alapszintjét **méterben**. Például: **Pince −3 m**, **Földszint 0 m**, **Emelet +3 m**. Ez nem másolja le az alsó szint falait: minden szintre külön rajzolhatók a helyiségek, falak, nyílászárók és bútorok.

Az alaprajz csak a választott szintet rajzolja, jelöli ki és illeszti; a **3D nézet és a fotórender az egész épületet** mutatja. A tulajdonságpanel magasságai a kiválasztott épületszint alapsíkjához viszonyított értékek; az épületszint alapszintjének módosítása az egész szintet mozgatja. A terep és a napfény közös. A korábbi tervek változatlan, 0 m-es földszintként nyílnak meg. A szintek módosítása visszavonható; **csak üres szint törölhető**, az utolsó nem.

**Lépcső az emeletre:** helyezd el az alsó szinten a lépcsőt, és állítsd a teljes magasságát a felső padló magasságához. Készíts megfelelő födémnyílást **az alsó szoba mennyezetén és a felső szoba padlóján is**; ezek külön szerkeszthető felületek. A felső padlót a lépcső végénél hagyd meg leszállóként. A séta a lépcsőfokokat követi, az egymás feletti padlók nem rántják fel a nézőt a felső emeletre. Nem statikai méretező vagy falütközés-szimulátor.

## Terasz, támfal és földfeltöltés

**Látszó beltéri fagerendák:** a katalógus **Gerendák → Vízszintes fagerenda** eleme alapból 4 m hosszú, 20 × 20 cm keresztmetszetű. Helyiségbe téve a középpontnál érvényes mennyezet alá kerül; a további változtatásokat kézzel kell követni. A **Szélesség** a gerenda hossza, a **Mélység** a vízszintes vastagsága, a **Magasság** a függőleges keresztmetszete. Az aránytartás alapból kikapcsolt, így ezek külön állíthatók. Az **Emelés** a gerenda alsó síkja az aktív épületszinthez képest; a **Forgatás** az alaprajzi irányát adja meg, a gerenda vízszintes marad. Az **Anyag** alatt a fa burkolata, mintamérete és saját textúrája is módosítható. Több párhuzamos gerendához másold az elsőt Ctrl+D-vel és helyezd át. Kültéren is használható.

Az eszközök az **Eszközök** menüből vagy a felső **Terep / terasz** lenyílóból érhetők el. Ha a felső eszköztár nem fér ki, a jobb széli **»** gomb alatt találod a további elemeket.

**Tetőtartó oszlopok a tornáchoz:** a katalógus **Oszlopok** csoportjából négyzetes és kör alapú oszlop helyezhető el. Húzd őket a tornácra, majd állítsd a magasságukat a járófelület és a tető alsó síkja közötti távolságra. Az **Emelés** az oszlop aljának szintje az aktív épületszinthez képest; a **Helyi padlóra helyezés** a terasz járószintjére teszi. Az oszlopoknál az aránytartás alapból ki van kapcsolva, így a magasság a vastagság változtatása nélkül állítható. A kör alak megtartásához a szélesség és mélység legyen egyforma. Az **Anyag** résznél közvetlenül választható fa, kő, márvány vagy más burkolat, saját textúrakép és mintaméret is. Az oszlopok kézzel elhelyezett látványelemek; nem igazodnak automatikusan a később módosított tetőhöz.

A **Terasz** eszközzel húzz téglalapot az alaprajzon. Ez külön, vízszintes, alapból korlát nélküli kültéri járófelület; a tulajdonságainál állítható a méret, forgatás, szint, lemezvastagság és burkolat. Az épületszinthez tartozik, magassága annak alapsíkjához viszonyított. A kültéri bútorokat a kész járófelületre helyezheted, és sétában is rá lehet lépni.

A **Támfal** eszközzel kattints a fal két végpontjára. A felső sík, vastagság és burkolat külön szerkeszthető; az alsó él követheti a lejtős terepet, vagy megadható hozzá önálló alsó szint. Ezek a falak nem helyiségfalak: a tereprendezéshez használhatók, nem hoznak létre szobát vagy tetőkörvonalat.

A **Földfeltöltés** eszközzel önálló földterületet rajzolhatsz a kilátszó betonalap elé; Shift nyomva tartásával ovális alakot húzhatsz. Felső szintje **abszolút világmagasság**, nem az éppen kiválasztott épületszinthez viszonyított érték. Az alak és forgatás mellett megadható a külső rézsű vízszintes szélessége és a felszín burkolata. A függőleges szél támfallal határolt kialakításhoz használható. A **Támfal a feltöltés köré** gomb egy lépésben függőlegesre állítja a peremet és külön szerkeszthető falakat készít; ezek a feltöltés későbbi módosításait nem követik. A feltöltés minden szinten közös, és az eredeti terepet csak emeli, nem végez földkiemelést. Az egymást fedő feltöltések közül a magasabb felület érvényesül; az épület belsejét nem töltik fel.

A kijelölés törlése után a **Projekt** tereprendezési beállításainál bekapcsolható az **automatikus rézsű** is. Ez a helyiségek alapja körül, kifelé futva takarja a betont. A rézsűszélesség méterben értett vízszintes kifutás; a felső eltolás a padló alatti szintet adja meg, alapból −15 cm. A magasabb emeletek nem építenek új földhalmot az alattuk lévő pince helyére. Az automatikus rézsű külön kapcsolható, és kézi feltöltésekkel együtt is használható. A talaj megjelenítését hagyd bekapcsolva.

A tervvel együtt mentődnek, visszavonhatók, és ugyanaz a geometria jelenik meg a 3D nézetben és a fotórenderben. A terasz, támfal és rézsű látványtervezési elem; teherbírás, vízelvezetés, talajnyomás és megengedett rézsűszög statikai/geotechnikai ellenőrzését nem végzi el a program.

## Domboldal, alapozás és pinceboltozat

A **Projekt → Domboldal és alapozás** részben állítható a terep **0–60°** közötti dőlése, az emelkedés iránya, a referenciaszint és annak alaprajzi X/Y pontja. **0° irány az alaprajz teteje / észak, 90° a jobb oldal.** A lejtős terep nem dönti meg az épületet: a padlók és emeletek vízszintesek maradnak. A referenciaszint az X/Y pontnál értendő; a talaj felülete a padlótól való elkülönítéshez 1 cm-rel lejjebb rajzolódik.

Az alapozás háromféle lehet:
- **Nincs alaptest:** csak terepfelület; a talaj a mélyebben fekvő szobák és padlóterületek helyén kivágódik.
- **Beton alaptest:** a helyiségek padlókörvonalát követő, vízszintes tetejű tömör alap. Az alsó sík a körvonal alatti legalacsonyabb terepszintnél az alapmélységgel mélyebbre kerül.
- **Dombba simuló / pince:** a belső térből kivágott terep, földoldalakkal és az alapmélységnek megfelelő vastag alsó alaplemezzel. A pince helyiségeit és falait külön épületszinten rajzold meg. Egymást fedő helyiségeknél a legalacsonyabb padlóhoz készül az alap, ezért a földszint alapja nem tölti fel a pincét.

Az alap burkolata külön állítható. Az alaptesthez **helyiségpadló is kell**, puszta falrajz nem hoz létre alapot. A belső süllyesztett padlórészeket az alapozás is követi. Ez látványtervezés, nem geotechnikai vagy statikai méretezés; a terep egy irányban lejtő sík, nem szabadon szobrászható domborzat.

**Boltozatos pince:** jelöld ki a helyiséget, majd a **Pinceboltozat** résznél válassz donga-, csúcsíves, lapos elliptikus vagy keresztboltozatot. Az elliptikus típus kisebb alap-emelkedéssel indul. Állítható az emelkedés, az X/Y tengelyirány és a díszítő bordázat. A burkolat a helyiség mennyezetének anyaga. A **Mennyezet → Magasság** itt a vállvonalat jelenti; a csúcs az emelkedéssel magasabban van. A következő épületszintet a csúcs és a 12 cm-es boltozathéj fölé helyezd. A boltozat a helyiség körvonalához vágott valódi 3D geometria, és a mennyezeti födémnyílások is kivágják; a 3D és a fotórender ugyanazt a héjat használja. A téglalap alaprajz adja a legszabályosabb boltíveket; más alaprajzoknál a körülírt téglalaphoz igazított boltozatot a helyiség alakja határolja.

## Kisebb szintkülönbségek, süllyesztett és emelt padlórészek

**Teljes helyiség:** jelöld ki a szobát az alaprajzon, vagy nyisd meg a Projekt panel **Helyiségek és mennyezet** listájából. A **Padló burkolat → Padlószint (abszolút)** mező a választott épületszint 0 cm síkjához viszonyított magasság. Például **−51 cm** három 17 cm-es fellépésnek megfelelő süllyesztés. Minden szoba külön állítható.

**Belső terület:** jelöld ki a szobát, válaszd a felső **Padlószint** eszközt vagy a jobb panel **+ Padlószint-terület** gombját, majd húzz téglalapot a szobán belül. A jobb panelen add meg a pontos méreteket, például **300×300 cm**, és a **Szinteltérés a szobához** értékét, például **−34 cm**. Ez a terület két 17 cm-es fellépéssel kerül lejjebb. Pozitív értékkel dobogó is készíthető. A szoba alap-padlójából a terület ténylegesen kikerül, új magasságú padlót és függőleges oldalfelületeket kap, így a felső padló és a külső talaj nem takarja el a mélyedést. A burkolat külön szerkeszthető; a mennyezetet nem vágja ki.

A terület sarkai és forgatófogantyúja húzható, az **R** forgatja; a jobb panelen téglalap, kör és ovális alak választható. A területeknek teljesen a saját szobán belül kell maradniuk, nem fedhetik egymást és nem ágyazhatók egymásba. A helyiség eltolásakor együtt mozognak vele. A födémnyílások padlókivágása az eltérő szintű padlón is érvényes.

**Lépcső egy gombbal:** a helyiség vagy a téglalap alakú szintterület paneljén a **Lépcső a szintkülönbséghez** résznél válaszd ki az oldalt, a fokszámot, a szélességet és a belépést, majd kattints a **Lépcső létrehozása** gombra. Teljes helyiségnél az adott él közepén túli szomszédos padló szintjét veszi alapul; helyiségen kívül a helyi terepszintet. A külön létrejött tömör lépcső a felső járószinthez csatlakozik, kijelölhető, mozgatható és tovább méretezhető. **Nem követi utólag automatikusan** a szoba vagy a terület módosítását; nagyobb változtatás után igazítsd vagy készítsd újra. Kör/ovális mellé a hagyományos Lépcső eszközzel helyezhetsz fokokat. A lépcső nem vág automatikusan át falat: két helyiség között ajtót vagy átjárót is készíts.

**Bútorok és séta:** az újonnan elhelyezett növény, bútor, szekrény és lépcső a helyi padlószintre kerül; helyiségen kívül a lejtős terepre. A meglévő tárgyak nem mozdulnak maguktól: normál tárgynál a **Helyi padlóra helyezés** gombbal igazíthatók, vagy az **Emelés / Alsó járószint** mezővel állíthatók, az épületszinthez képest. Séta indításakor a szemmagasság a helyi padló felett 160 cm. Vízszintes mozgáskor és görgetéskor követi az egyenes és a csigalépcső fokait, fel- és lefelé is; Q/E-vel továbbra is változtatható a nézőmagasság. A paraméteres lépcsők megengedett, legfeljebb **40 cm-es** fellépése követhető; lépcső nélküli padlóperemnél a felfelé lépés határa 36 cm. Ez nem ütközésvizsgálat vagy teljes fizikai járásszimuláció: falon továbbra is áthaladhatsz, és támasz nélküli részen a néző alacsonyabb járófelületre kerülhet. A födémnyílásokat külön kell kialakítani.

**Magasságok:** a helyiségen belüli padlószint-állítás nem mozdítja el a falakat, ajtókat/ablakokat vagy a meglévő bútorokat; az egész emelet áthelyezéséhez az épületszint magasságát használd. A helyi padlószintek −1000 és +1000 cm között állíthatók; bekapcsolt mennyezet alatt legalább 10 cm geometriai távolság szükséges. A megengedett tartomány nem építési vagy biztonsági előírás. A változások menthetők és visszavonhatók, a 3D nézet, fotórender és sétafelvétel ugyanazt a geometriát használja.

## Kutak, vízellátás, lakókocsi és kerítések

A **Kutak és vízellátás** kategóriában házi vízszivattyú, kerekes kút, tetős kerekes kút és kéziszivattyús kút található. A kutak üreges kávával, a kerekes változatok kötéllel és vödörrel készültek. A **Lakókocsik** csoport saját tervezésű, márkajelzés nélküli túralakókocsit tartalmaz, kerekekkel, ablakokkal, ajtóval, fellépővel és vonórúddal. Nem egy konkrét gyártói típus másolata; személyautó-modellek nem kerültek a csomagba. A szokásos bútorvezérlőkkel méretezhetők és forgathatók.

**Kerítés készítése:** rajzolj falat, jelöld ki, majd a jobb oldali **Fal / kerítés típusa** mezőben válassz függőleges léckerítést, vízszintes léckerítést, fém pálcás kerítést, rácsos drótkerítést vagy tömör falazott kerítést. Az áttört típusok valódi térbeli elemekből állnak. A hossz, ívelés, vastagság, magasság és oszlopköz állítható; a **Kerítés anyaga** alatt szín, burkolat és saját textúra is választható.

A **Kerítés követi a terepet** alapból bekapcsolt: ilyenkor az emelés a helyi terepfelszínhez képest értendő. Kikapcsolva a kerítés alja az aktív épületszint alapsíkjához képest vízszintes. A kerítés nem növeli a ház automatikus tetőkontúrját vagy mennyezetmagasságát, és nem helyettesíti a helyiségek épületfalait. Ajtót/ablakot tartalmazó fal és összekapcsolt rotundafal nem alakítható kerítéssé. Kapuhelyhez hagyj üres szakaszt két kerítés között; külön kapuszerkesztő nincs. Ezek látványtervi elemek, nem statikai vagy kivitelezési számítások.

## Csoportosított bútorkatalógus és egyedi szekrény

A katalógus **190 saját, márkajelzés nélküli modellt** tartalmaz: 15 fürdőszobai és 175 otthoni/kerti/növény-, oszlop-, gerenda-, vízellátási és lakókocsimodellt. A kategóriaválasztó és a szöveges kereső együtt szűr:

- **Székek, asztalok, kanapék, fotelek:** étkező- és bárszék, irodaszék, pad, különböző asztalok, két-/háromszemélyes és sarokkanapék, fotelek és lábtartó.
- **Szekrények:** két-/háromajtós ruhásszekrény, fiókos komód, könyvespolc és TV-szekrény.
- **Szaniterek és fürdőszobabútorok:** mosdók, WC-k, bidé, kádak, zuhanykabin, mosdószekrények és további fürdőszobai tárgyak.
- **Konyhafelszerelés és mosogatók:** alsó-, fiókos és falielem, sziget, vágódeszka, fazék, vízforraló; egy-/kétmedencés és csepegtetős mosogató.
- **Konyhai gépek:** 45/60 cm-es mosogatógép, mikró, beépíthető sütő, tűzhely, főzőlap, páraelszívó és kávéfőző.
- **Hűtőszekrények:** kombinált, egyajtós, pult alatti, side-by-side és French-door változat.
- **Szórakoztatóelektronika:** 43/55/65 colos TV, hi-fi torony hangfalakkal, külön álló hangfalpár, hangprojektor és játékkonzol.
- **Számítógépek és tartozékok:** számítógépház, 27 colos monitor, nyitott laptop, billentyűzet egérrel, nyomtató és egybeépített számítógép.
- **Könyvek:** álló könyv, 16 könyvből álló könyvsor, ötkötetes könyvhalom és nyitott könyv.
- **Radiátorok és konvektorok:** gázkonvektor, alumínium- és öntöttvas radiátor, 60/120 cm-es lapradiátor, cellás radiátor, elektromos konvektor, olajtöltésű elektromos radiátor és fali elektromos fűtőpanel.
- **Ventilátorok:** asztali, fali, mennyezeti, álló és oszlopventilátor.
- **Ágyak:** 90 cm-es egyszemélyes, 160/180 cm-es kétszemélyes/franciaágy, 80 cm-es gyerekágy. Valódi ágykeret, matrac, párna és takaró; nem kanapé.
- **Lámpatestek:** ötkarú csillár, háromrészes függeszték, UFO mennyezeti lámpa, állólámpa és fali olvasólámpa.
- **Előszobabútorok:** álló ruhatartó, cipősszekrény, akasztós előszobafal paddal/polccal, nyitott cipőtartó és vállfás ruhatartó állvány.
- **Villamos berendezések:** beltéri/kültéri villanyóraszekrény, napelemes inverter, külön Wi-Fi modul, tetőre helyezhető TV-antenna és parabolaantenna.
- **Napelemek:** külön tetőre és talajra szerelhető panel. A katalógusból elhelyezve paraméteresen szerkeszthető, nem pusztán statikus OBJ. A **Dőlésszög** 0–85° között, a vízszinteshez képest állítható; a támasztékok automatikusan követik, talajon álló talpuk lent marad. A panel szélessége és hossza 30–500 cm között szerkeszthető a jobb panelen; a magasság és a vízszintes befoglaló méret automatikusan változik. A méretet itt add meg, nem a szokásos bútorsarok-húzással, hogy a tényleges dőlésszög ne torzuljon. Az **Emelés** és **Forgatás** mezővel illeszd a tető vagy talaj helyére; nincs automatikus tetőillesztés, energiatermelési vagy tartószerkezeti számítás. A dőlés menthető, visszavonható és egymástól független másolatokon állítható; a 3D nézet és a fotórender ugyanazt a geometriát használja. Az OBJ-export az alapértelmezett 30°-os katalógusváltozat.
- **Kerti bútorok és felszerelések (20 modell):** téglalap/kerek asztal, szék/karosszék, pad, napozóágy, kerti kanapé, kőgyűrűs szalonnasütő nyárstartókkal, grillsütő, háromszemélyes és fekvő hintaágy, napernyő, virágláda, padokkal egybeépített piknikasztal. Új támlás és háttámla nélküli fapad, 200 cm-es faasztal és két-, négy-, hatszemélyes sörpadgarnitúra. Statikus látványmodellek, nem kivitelezési vagy tűzvédelmi tervek; nincs tűz- vagy hinta-animáció.
- **Bojlerek és klímák (4 modell):** gázbojler kéménycsatlakozással, hengeres villanybojler, lamellás beltéri klíma és ventilátorrácsos kültéri egység. A két klímaegység külön helyezhető el, a fali gépeket az **Emelés** és **Forgatás** mezőkkel illeszd. Statikus látványmodellek, nem gáz-, víz- vagy villamos bekötési tervek.
- **Medence és felszerelései (3 modell):** 6×3 m belméretű, 1,46 m magas, felszíni téglalapmedence peremmel, belső lépcsővel és színezett, fényes vízfelülettel; külön rozsdamentes kapaszkodókorlát; szivattyús vízforgató homokszűrővel és csövekkel. A korlát alját **Emeléssel** helyezd a medence peremére. A medence nem vágja ki automatikusan a talajt vagy a padlót, a víz nem animált és nem szimulált; nem uszodagépészeti vagy biztonsági terv.
- **Kerti házak és tárolók (4 modell):** 3×2,4 m-es kerti kisház ablakkal és nyeregtetővel, valamint 1,8×1,2 m-es szerszámtároló. Új nádtetős **palapa** és fa **filagória**, nyitott kerti pavilonként. Önálló, méretezhető objektumok; nem szerkeszthető helyiségek vagy építési tervek. A kisház és a tároló ajtaja statikus.
- **Kerti növények (28 modell):** lombos fa, gyümölcsfa, nyírfa, fenyő, oszlopos tuja, gömbtuja, bokor, virágzó cserje, sövény, rózsabokor, tulipánok, virágágyás, fürtös szőlőtőke és oszlopokkal/huzalokkal együtt készített kordonos szőlő. Új, elkülönülő ágakkal, levelekkel és termésekkel rajzolt **cseresznye, körte, datolyaszilva / sharon, dió, meggy, alma és naspolya**; **szőlőlugas, vörös és fehér szőlőtőke**; **ribizli, egres, málna és áfonya**.
- **Szobanövények (8 modell):** cserepes fikusz, monstera, pálma, anyósnyelv, pozsgás, kaktusz, orchidea és muskátli. A kaspó és a növény együtt méretezhető; a növények statikus, eredeti, egyszerűsített geometriai látványmodellek, nem fotószkennelt botanikai modellek.

### Sétafelvétel MP4 videóba

A 3D nézet **REC** gombjával válaszd ki a videó mentési helyét. A program séta módba kapcsol; mozogj W/A/S/D-vel, fordulj nyilakkal vagy egérhúzással. Az időszámláló jelzi az aktív rögzítést. A **Stop** lezárja a felvételt; a háttérben készülő fájl véglegesítése után **Mentve: fájlnév.mp4** jelenik meg. Kilépés előtt ezt várd meg. Új felvételt ezután indíthatsz.

A beépített Java-kódoló hang nélküli H.264/MP4 videót készít, **15 kép/mp** sebességgel, az induló 3D nézet képarányával, legfeljebb **960×540** felbontásban. Nem szükséges FFmpeg vagy más külön program. A valós idejű 3D képet veszi fel, nem a fotórenderelőt; a kezelőpanelek és a 2D feliratok nem kerülnek a videóra. Ablakátméretezéskor a videóméret változatlan, szükség esetén fekete sávok őrzik meg a képarányt. Lassabb gépen képkockák ismétlésével tartja a valós felvételi időt; nem gyűjti korlátlanul memóriába a képeket. A felvétel a Stopig folytatódik akkor is, ha másik panelre kattintasz. Mentési hiba esetén hibaüzenetet ad, és a meglévő célvideót nem írja felül.

A lámpatestek külön látványmodellek. Tényleges helyiségvilágításhoz helyezz melléjük **Lámpa** fényforrást a felső eszköztárról, ahol a fény színe és erőssége is szerkeszthető. Mennyezeti lámpatestnél az **Emelés = mennyezetmagasság mínusz a modell magassága**. A fali tárgyakat és a Wi-Fi modult szintén az Emelés és Forgatás mezőkkel illeszd a helyükre. Az inverter és a villanyóraszekrény márkajelzés nélküli statikus modell, nem villamos kapcsolási/bekötési vagy érintésvédelmi terv.

A TV-k talppal készültek. A könyveket, számítógépes eszközöket és asztali ventilátort a bútor tetejére az **Emelés** mezővel helyezd. A fali ventilátor/radiátor alját szintén ezzel emelheted a szerelési szintre. A mennyezeti ventilátornál az **Emelés = mennyezetmagasság mínusz a modell magassága**, hogy a felső rögzítőtalp a mennyezethez illeszkedjen. A forgatás a falhoz igazítást szolgálja. Ezek statikus látványtervezési modellek: nincs ventilátoranimáció, működő kijelző, fűtésszámítás vagy automatikus fal-/mennyezeti rögzítés; a gázkonvektor nem gépészeti bekötési terv.

Dupla kattintással vagy áthúzással helyezhetők el; méretük, forgatásuk, emelésük és felületük a szokásos bútorvezérlőkkel módosítható. Külön hűtőszekrény-szerkesztő nincs, a katalógus öt típust ad. Az új OBJ/MTL modellek az `objects\HelmetSoft_Otthon` kategóriamappáiban vannak, CC0-1.0 licenccel. A **teljes csomagot a gyökérben lévő közös PNG textúrákkal együtt** másold, hogy megmaradjanak az anyagok. Az EXE-építés újragenerálja a név szerinti állományokat; egyéni módosítást másik mappába ments.

**Egyedi szekrény:** a katalógus **+ Egyedi szekrény** gombjával vagy a **Szerkesztés** menüből adható hozzá. A jobb panelen szélesség, mélység, magasság, rekeszszám, rekeszenkénti polc- és fiókszám, lapvastagság és lábazat állítható. Az ajtók kapcsolhatók és 90°-ban nyithatók; a polcok a fiókok fölött helyezkednek el. Külön szerkeszthető a korpusz/polcok, a frontok és a fogantyúk anyaga. A méretek a korpuszra vonatkoznak; a nyitott ajtó és a fogantyú kilóghat. A beállítások menthetők, visszavonhatók és függetlenül másolhatók. A modell az alaprajzon, 3D-ben és fotórenderben azonos szerkezetű. Ez **paraméteres látványterv**, nem gyártási dokumentáció vagy szabásjegyzék.

## Kerekített ajtók, átjárók és ablakok

Nyílászáró kijelölése után a **Felső él** mezőben válassz téglalapot, félkört vagy bal/jobb oldalon magas negyedkört. Ajtón, ajtó nélküli átjárón, ablakon és erkélyajtón is működik, egyenes és íves falban. A fal tényleges kivágása, a keret, az üvegezés és a nyitott ajtólap is a profilt követi.

A magasság a párkánytól az ív legmagasabb pontjáig értendő. Félkörnél a sugár a szélesség fele, negyedkörnél a teljes szélesség. Az ív alatt legalább **15 cm egyenes oldalfal** maradjon: például 90 cm széles félkörhöz legalább 60 cm, ugyanilyen negyedkörhöz legalább 105 cm teljes magasság kell. A hibás vagy falon túlnyúló profilt a program nem fogadja el; előbb növeld a magasságot vagy csökkentsd a szélességet.

**Kilincs és részleges ajtóüvegezés:** az ajtók mindkét oldalán fémkilincs látható, amely követi a nyitásirányt és a zsanér tükrözését; az erkélyajtók is kapnak kilincset. Az **Ajtó** típus tulajdonságainál az **Ajtólap üvegbetét → Részleges üvegezés** bekapcsolásával egy téglalap alakú üvegbetét készül, nem az egész ajtólap válik üveggé. A **Bal szél / Alsó szél / Üveg szélessége / Üveg magassága** centiméterben állítható, az ajtó bal és alsó szélétől mérve. Legalább 8 cm tömör szegély maradjon körben, az íves felső él alatt is; érvénytelen beállításnál a módosítás nem kerül mentésre. Az ajtó átméretezésekor a betét arányosan változik. Tükrözéskor a betét együtt mozog az ajtólappal. A profil, kilincs és üvegezés a 3D nézetben és a fotórenderben közös geometriából készül; a paraméterek menthetők és visszavonhatók.

## Mennyezet és görgethető súgó

A 3D **Mennyezet** kapcsolója induláskor bekapcsolt állapotú, és a séta elhagyása nem kapcsolja ki. A helyiség **Van mennyezet** beállítása ettől független: annak is bekapcsolva kell lennie. A külön rajzolt **Padló (sokszög)** továbbra sem kap automatikus mennyezetet; a helyiség tulajdonságainál engedélyezhető. A födémnyílások csak a saját területüket vágják ki, nem tüntetik el a mennyezet többi részét.

**A helyiség beállításainak biztos elérése:** ha jobb oldalon a **Projekt** panel látszik, a **Helyiségek és mennyezet** részben válaszd ki a szobát, és kattints a **Helyiség megnyitása** gombra. Ekkor a **Mennyezet → Van mennyezet** kapcsoló elérhető, alaprajzi kattintás nélkül is. A listához kijelölés megszüntetésével (Kijelölés módban Esc) térhetsz vissza. Ha nincs helyiségfelület, a panel ezt jelzi és felajánlja a padló körberajzolását: a falak önmagukban nem hoznak létre mennyezetet.

A **Súgó → Gyors útmutató (F1)** ablaka átméretezhető, a teljes szöveg görgethető.

## Fürdőszobai modellcsomag

A bal oldali katalógusban válaszd a **Szaniterek** vagy **Fürdőszobabútorok** kategóriát. A kereső ékezet nélkül és ékezettel is működik (például „mosdó”, „kád”, „zuhany”). Dupla kattintással vagy áthúzással helyezhetők el a modellek, természetes, méterhelyes méretben:

- 80 cm-es mosdószekrény és 120 cm-es dupla mosdószekrény, mosdóval és csapteleppel;
- talpas mosdó és külön, pultra helyezhető mosdótál;
- tartályos WC, fali WC és bidé;
- 170 cm-es szabadon álló és egyenes kád;
- 90 cm-es, üveges zuhanykabin és 120 cm-es walk-in zuhanyfal tálcával, zuhanyszerelvénnyel;
- magas fürdőszobaszekrény, tükrös szekrény, mosógép és törölközőszárító.

Minden tárgy forgatható, méretezhető és másolható, mint a többi bútor. A mosdótálat a pult tetejére, a fali WC-t, tükröt és törölközőszárítót a kívánt szerelési magasságra az **Emelés** mezővel helyezd: az importált modell alja alapértelmezetten a padlón van. A névben szereplő méret névleges; a csaptelep, fogantyú vagy csatlakozás kilóghat ebből.

A modellek **saját, márkajelzés nélküli, CC0-1.0 licencű** alkotások: szabadon módosíthatók és kereskedelmi munkában is felhasználhatók. Az `objects\HelmetSoft_Furdoszoba` mappában önálló **OBJ + MTL** fájlokként is megtalálhatók. Az OBJ-t, MTL-t és az esetleges PNG textúrát együtt másold; a mértékegység méter, a függőleges tengely Y. Az EXE-építés külön `dist\HelmetSoft_Furdoszoba.zip` csomagot is készít. A mosdók és kádak valódi belső felületet kapnak, a zuhanyüveg a 3D nézetben és a fotórenderben is átlátszó. A csomag nem gyártási terv és nem konkrét gyártók termékmásolata.

Az EXE-építő újragenerálja a csomag név szerinti fájljait, ezért saját módosításhoz készíts másolatot másik néven/mappába. Külön regenerálás fordítás után: `java -cp "target\dist\*" hu.helmetsoft.homedesigner.io.BuiltinModels "objects\HelmetSoft_Furdoszoba"`.

## Egyenes lépcső és csigalépcső

- **Hozzáadás:** a felső **Lépcső** menüből, a **Szerkesztés** menüből vagy a katalógus alatti **+ Lépcső** gombbal. A nézet közepére kerül; húzással vagy az X/Y mezőkkel helyezhető el, a forgatófogantyúval és a **Forgatás** mezővel tájolható.
- **Szintmagasság:** az induló járószint és az utolsó fok teteje közötti magasság. Az **Alsó járószint** emeli az egész lépcsőt, a **Korlátmagasság** ezen felüli. A méretfelirat harmadik értéke a szintmagasság, nem a korláttal együtt mért teljes magasság.
- **Fokszám és fokvastagság:** közös beállítások. A fellépést automatikusan számolja a program (`szintmagasság / fokszám`). Egyenes lépcsőnél 1–80, csigalépcsőnél 3–80 fok és 5–40 cm fellépés engedett; a járólap nem lehet vastagabb a fellépésnél. Ezek geometriai határok, nem építési előírások.
- **Egyenes lépcső:** a szélesség és a teljes vízszintes hossz külön állítható; a kijelzett belépés a hossz osztva a fokszámmal. Nyitott fokok és két tartó, vagy **Tömör lépcsőtest**, valamint kapcsolható kétoldali korlát készül. Egyetlen vagy két fok is használható kis szintkülönbségekhez. Alaphelyzetben a negatív Y irányból a pozitív Y felé emelkedik; az alaprajzi nyíl felfelé mutat.
- **Csigalépcső:** külső átmérő, belső/oszlopsugár, 90–720° teljes fordulat és az emelkedés forgásiránya állítható. A fokok a központi oszlop körül futnak, a külső korlát követi az emelkedést. A sarokfogantyús méretezés megtartja a kör alakját. Egy fok legfeljebb 60°-ot foglalhat el. Több fordulatnál a panel tájékoztatásul mutatja az egymás fölötti körök közti szabad magasságot.
- **Burkolatok:** a fokok és a tartószerkezet/korlát anyaga, színe és textúrája külön választható. A geometria minden paramétermódosításkor újraszámolódik; nem egy rögzített lépcsőmodell egyszerű nyújtása.
- **Mentés és szerkesztés:** visszavonás/újra, másolás, törlés és projektmentés támogatott. A másolat paraméterei és anyagai egymástól függetlenek. A 2D alaprajz, a valós idejű 3D és a fotórender ugyanazt a lépcsőt használja.

**Fontos:** a lépcső látványtervezési elem, nem statikai/kivitelezési terv, és nem ellenőrzi a helyi építési, akadálymentességi vagy korlátbiztonsági előírásokat. Nem hoz létre emeletet vagy külön felső pihenőt. A Séta mód az egyenes lépcsőfokok magasságát követi, de nincs ütközésvizsgálat vagy csigalépcső-követés. A hozzá tartozó födémnyílás az alábbi gombokkal készíthető el.

## Mennyezet- és padlókivágás

- **Lépcső fölé:** jelöld ki a lépcsőt, majd a jobb panel **Födémnyílás a lépcső fölé** részében válassz **Téglalapnyílás**, **Környílás** vagy **Ovális nyílás** gombot. A lépcső jobb gombos menüjében is készíthető hozzá illő nyílás. A teljes vízszintes vetületet lefedő, ráhagyásos **önálló mennyezetnyílás** készül; nem követi később a lépcső mozgatását/méretezését. Több egymást fedő helyiség esetén kiválasztható a célhelyiség.
- **Szabad rajzolás:** a felső **Födémnyílás** menüben téglalap, kör, ovális és szabad sokszög választható. A téglalapot/ovális befoglaló téglalapját húzd ki az alaprajzon; körnél középpont és sugárvégpont szükséges (a sugár méterben beírható + Enter). Sokszögnél kattints a csúcsokra, majd az első pontra, vagy dupla kattintással / Enterrel / jobb kattintással zárd. Esc megszakítja a még nem kész nyílást.
- **Célhelyiség:** a nyílás ahhoz a helyiséghez tartozik, amelyben elkezded rajzolni; előzetesen kijelölt helyiség vagy nyílás esetén annak helyiségéhez. Egymást fedő padlófelületeknél előbb jelöld ki a kívánt helyiséget. A nyílás csak ennek a felületét vágja, a helyiséghatáron túli része nem vágja a szomszéd épületet. Az átfedő nyílások egyesülnek. A helyiség mozgatása a saját nyílásait is viszi, törlése azokat is törli.
- **Külön kapcsolók:** az új nyílás alapértelmezésben csak a mennyezetet vágja, a helyiség mennyezetét engedélyezi. A nyílás jobb paneljén a **Mennyezet kivágása** és a **Padló kivágása** egymástól függetlenül kapcsolható. Mindkettő kikapcsolva átmenetileg inaktív nyílást jelent. A 3D nézet **Mennyezet** kapcsolóját kapcsold be a mennyezet ellenőrzéséhez; a fotórender is a kivágott geometriát használja.
- **Szerkesztés:** a körvonalra kattintva kijelölhető, húzható, a jobb panelen pontos középpont, forgatás és méretek adhatók meg. A téglalap/kör/ovális sarkai méreteznek, a kiálló fogantyú forgat. Sokszögnél a csúcsok húzhatók, a teljes alak a szélesség/hosszúság mezőkkel méretezhető; pontok a jobb panelen beszúrhatók és törölhetők. A kör mindig kör marad. Bútor alatt a körvonal elsőbbséget kap a kijelölésnél; a helyiség paneljéről is kiválaszthatók a nyílások.
- **Mentés:** visszavonás/újra, másolás, törlés és projektmentés támogatott. A nyílás valós geometriát távolít el a 3D és fotórender felületeiből, nem takarófolt. Az alaprajzon a mennyezetnyílás szaggatott, a padlót is kivágó nyílás folytonos körvonalú.

**Korlátok:** a program jelenleg felületszerű padlót és mennyezetet használ, nem vastag szerkezeti födémet. A padló alatti globális talaj megmarad, kivéve a negatív szintű helyiségek és padlórészek alatt; a terv környezeti beállításaiban teljesen kikapcsolható. Nincs automatikus többszintes épület, fejmagasság- vagy statikai ellenőrzés; a nyílás elhelyezését és a lépcső felső kijáratát ellenőrizd a 3D nézetben. A nyílás nem vág tetőt, falat vagy másik helyiség felületét.

## Ívelt falak és rotunda

- **Ívelt fal / kör → Ívelt fal (3 pont):** kattints a kezdőpontra, az ív egy köztes pontjára, majd a végpontra. Az előnézet mutatja a körívet, a sugarat és az ívhosszt. 180°-nál nagyobb körív is rajzolható, legfeljebb 350°-ig; egy egyenesre eső pontokat a program visszautasít. Esc vagy jobb kattintás megszakítja a befejezetlen ívet.
- **Rotunda / körfal:** kattints a középpontra, majd a sugár végére. Pontos sugárhoz gépeld be a métert (tizedesvessző is használható), majd Enter. Négy összekapcsolt negyedív és körpadló készül. A sugár 30 cm–100 m lehet, a falvastagságtól is függően.
- **Szerkesztés:** a fal középső fogantyúját húzva hajlíthatsz, a végpontokkal az ív végei mozgathatók. A jobb oldali panelen az ívszög (0° = egyenes; előjelváltás = ellenkező ív), az ívhossz, a sugár, a magasság, a vastagság és mindkét oldal burkolata állítható. Önálló ív sugarának/hosszának módosítása a kezdőpontot és az ívszöget tartja meg, a végpont változik.
- **Rotunda méretezése:** jelöld ki a körpadlót, vagy a körfal jobb oldali paneljén válaszd a **Rotunda / körpadló kijelölése** gombot. A sugár, a középpont, az összes körfal vastagsága és magassága itt állítható. A jobb oldali sugárfogantyúval is átméretezhető. Mozgatáskor és sugármódosításkor a négy fal, a padló és a nyílások helye együtt változik; a nyílászárók szélessége nem skálázódik.
- **Ajtó, ablak, erkélyajtó, átjáró:** ugyanúgy hozzáadható és a fal mentén húzható, mint egyenes falon; a 3D-ben és a fotórenderben valódi falnyílás készül. A szélesség és a fal elejétől mért távolság **a fal középívén** értendő. A hagyományos, sík nyílászáró a belső ív **húrjára** kerül, ezért fizikai keretszélessége kisebb az íven megadott méretnél. Egy nyílás legfeljebb 120°-os ívet foglalhat el, a keretnek legalább 20 cm húrhossz kell, a nyílások nem fedhetik egymást. A kör negyedíveinek csatlakozását egy nyílás nem keresztezheti.
- **Fal kettévágása:** a jobb gombos menü megőrzi a körívet és a hozzá tartozó nyílásokat; nyíláson keresztül nem vág. Rotundánál a kettévágás vagy a **Felbontás önálló ívekre** parancs megszünteti a csoportos sugárszerkesztést, a falak és a körpadló megmaradnak önálló elemekként. Egyetlen körfal törlése szintén felbontja a csoportot; az összekapcsolt körpadló törlése az egész rotundát törli. Minden művelet visszavonható.

A körpadló kerületét és a falhálót finom szegmensekből építi a program (legfeljebb 1 mm középív-eltérés); a faloldalak simított normálokat és ívhossz szerint futó textúrát kapnak. A sugár és az alapterület a fal **középvonalára** vonatkozik. A méretvonalon az **ívhossz** szerepel, kijelölt önálló ívnél a sugár is, nem a húrhossz. A korábbi, egyenes falakat tartalmazó projektfájlok változtatás nélkül megnyithatók. Önálló ívekből épített, szabálytalan helyiség padlóját a **Padló (sokszög)** eszközzel kell megrajzolni; automatikus körpadlót a rotunda eszköz ad.

## Íves tetők és különálló épületek

- **Épületenként külön tető:** jelöld ki az adott épület egyik falát vagy helyiségét, majd **Tető / erkély → Új tető a falakra**. A fal jobb gombos menüjéből is hozzáadható. Több különálló épület esetén a program kijelölést kér, nem húz közös tetőt az összes fal fölé. A külső falaknak zárt körvonalat kell alkotniuk.
- **Ívet követő körvonal:** az automatikus illesztés a falak külső oldalát követi, a köríves és az egyenes szakaszok kombinációját is. A túlnyúlás ettől kifelé, egyenletes távolságra készül, lekerekített sarkokkal. A 2D alaprajz, a 3D nézet és a fotórender ugyanazt az alakot használja; a befoglaló téglalap üres sarkai nem kapnak tetőt.
- **Rotunda:** illesztéskor automatikusan **kúptetőt** kap. A jobb oldali **Típus** mezőben **Kupolatetőre** válthatsz. A kúp hajlásszöge, a kupola emelkedése, továbbá az ereszmagasság, a sugár, a túlnyúlás és a burkolat szerkeszthető. A kupola ellipszoid alakú, a csúcsa az ereszmagasság és a megadott emelkedés összege, plusz a fedés vastagsága. A sugár a külső falvonalig értendő, nem tartalmazza a túlnyúlást. Sugármódosításkor megmarad a tető középpontja.
- **Önálló kör alap:** meglévő tetőn a **Kör alap** kapcsolóval választható; utána állítható kúp vagy kupola. A kör nem nyújtható ellipszissé, a sarokfogantyúk megtartják a kör alakját. A kör alap kikapcsolása téglalappá alakítja a tetőt.
- **Falváltoztatás után:** a tető önálló elem, nem módosul automatikusan a falakkal. Jelöld ki, majd **Falakhoz igazítás**: csak az alatta lévő épület új körvonalára illeszkedik. Típusa, fedése és beállításai megmaradnak. Ha a meglévő tetőablakok már nem férnének el, a művelet hibaüzenettel visszavonódik.
- **Síkokból álló tetők:** a nyereg-, kontyolt, félnyereg- és lapos tető kör vagy szabálytalan alapra is tehető. Ezek a hagyományos, befoglaló méretekből számított tetősíkok körvonalra vágott változatai; nem automatikusan szerkesztett, minden fal mentén azonos ereszmagasságú, tetszőleges alaprajzú kontytetők. A szükséges fal feletti lezárást az **Oromfal / fal feletti lezárás** kapcsoló adja.

**Korlátok:** kúpra és kupolára jelenleg nem helyezhető tetőablak vagy süllyesztett tetőterasz. Kör vagy íves körvonalú, síkokból álló tetőn ezek továbbra is használhatók, ha az elem teljes területe elfér egyetlen tetősíkon, a tényleges falvonalon belül. Az illesztés a legkülső körvonalat fedi le; belső udvar számára nem készít automatikus tetőnyílást. Szabálytalan tető szélességének és mélységének külön módosítása a teljes körvonalat arányosan nyújtja az adott irányban, az íves részeket is. A korábban mentett téglalap alakú tetők megmaradnak; íves falakhoz a **Falakhoz igazítás** gombbal frissíthetők.

## Tetőablakok, tetőteraszok és erkélyek

- **Tetősíkablak:** jelöld ki a tetőt, majd a **Tető / erkély → Tetősíkablak** parancsot. Kerete és üvegezése a tetősíkot követi, és a fedésen, valamint a tető alsó oldalán valódi nyílás készül.
- **Kiugró tetőablak:** külön oldalfalakat és függőleges, üvegezett homlokzatot kap. Saját fedele lehet nyereg-, félnyereg- vagy lapos tető; a kiemelés a meglévő tetősík legmagasabb érintett pontjától mérendő. Fedése a szülő tető anyagát használja.
- **Süllyesztett tetőterasz:** kivágja a tetőt, süllyesztett járófelületet, oldalfalakat, hátsó üvegezett bejáratot és elülső mellvédet/korlátot kap. A süllyesztés a nyílás legalacsonyabb tetőpontjától mérendő. A korlátmagasság a terasz padlójától értendő; ha a tető pereme már magasabb ennél, a perem tömör mellvédként marad meg.
- **Homlokzati erkély:** a tetőtől független lemez, állítható szélességgel, mélységgel, padlószinttel, forgatással és lemezvastagsággal. Pálcás, üveges vagy tömör korlát választható, oldalanként ki/be kapcsolva. Az X/Y a hátsó bal sarok; 0°-nál az erkély a pozitív Y irányba nyúlik ki. Erkélyajtóhoz kattints jobb gombbal a falra, majd válaszd az **Erkélyajtó ide** parancsot. Az ajtót és az erkély padlószintjét külön kell összehangolni.

A tető kijelölése után a jobb oldali panelen is hozzáadhatók és kiválaszthatók a tetőelemek. Több tető esetén előbb jelöld ki a megfelelőt. Az új elem szabad tetősíkra kerül; az alaprajzon húzással áthelyezhető, a jobb oldali panelen centiméterben átméretezhető. Az X/Y a **szülő tető falsarki kezdőpontjától** mért alaprajzi távolság, a szélesség és mélység **vízszintes vetület**, nem ferde síkon mért hossz.

Egy tetőelem nem keresztezhet gerincet vagy kontyélt, a falvonalon belül legalább 5 cm hely, az elemek között legalább 10 cm szükséges. Hibás átméretezést vagy tetőforma-váltást a program visszautasít; alaprajzi húzásnál az állapotsor jelzi a korlátozást. A tetőelemek együtt mozognak a szülő tetővel. Másolhatók, törölhetők, visszavonhatók, menthetők, és a fotó renderben is megjelennek. A **Tető** kapcsoló a hozzá tartozó tetőelemeket is elrejti, a homlokzati erkélyeket nem. Belső tetőtéri nézetnél kapcsold ki a **Mennyezet** kapcsolót, ha a vízszintes szobamennyezet eltakarja a tetőablakot.

Ez látványtervező, nem statikai vagy kivitelezési tervezőprogram: a tartószerkezetek, csatlakozások, vízszigetelés és előírások ellenőrzése külön szakági feladat.

## Tesztek

`mvn test`: geometriai tesztek (körívek, rotunda, falcsatlakozások, nyílások, tetőkivágások, tetőformák, korlátok, régi projektfájlok), render-sugármetszési tesztek (üvegezés, átjárók, folytonos körfal és gyorsított/pontos metszések összevetése 360 irányban) és JavaFX szerkesztési tesztek (egérrel rajzolás, pontos sugár, hajlítás, méretezés, másolás, törlés, visszavonás). A JavaFX tesztekhez Windows grafikus környezet szükséges. Opcionálisan `-Dhd.testSnapshots=C:\teljes\eleresi\ut` paraméterrel a felületi teszt képernyőképeket is ment.

## Modellformátumok

| Formátum | Támogatás |
|---|---|
| OBJ (+MTL, textúrák) | Saját betöltő, teljes támogatás. |
| FBX, 3DS, DAE, GLTF/GLB, PLY, STL stb. | Assimp könyvtárral. |
| BLEND | Ha a gépen telepítve van a Blender, azzal konvertálja (ez a legmegbízhatóbb). Ha nincs, a beépített olvasóval próbálja; azt a program egy külön folyamatban ellenőrzi, így egy hibás fájl nem állítja le az alkalmazást. Végül egy mellette lévő, azonos nevű OBJ/FBX fájlt keres. |
| MAX | Zárt Autodesk formátum. Ha telepítve van a 3ds Max, azzal konvertálja. Ha nincs, egy mellette lévő, azonos nevű OBJ/FBX/3DS exportot tölt be. |

## Felépítés

- `model`: adatmodell (falak, helyiségek, nyílászárók, bútorok, tetők, lámpák).
- `geometry`: 3D hálók generálása (falak nyílásokkal, padló, tető).
- `io`: modellbetöltők és projektmentés.
- `render`: CPU-s path tracer és zajszűrő.
- `ui`: JavaFX felület (alaprajz, 3D nézet, tulajdonságpanel, render ablak).

## Licenc

A projekt a [PolyForm Noncommercial 1.0.0](LICENSE) licenc alatt áll: szabadon
nézhető, módosítható és terjeszthető, de csak nem kereskedelmi célra.
Az `objects/HelmetSoft_Furdoszoba` és `objects/HelmetSoft_Otthon` saját,
CC0 licencű modellek (lásd az ottani `LICENSE.txt` fájlokat).
