var sisteDagsmeny = null;

function getCsrf() {
    var m = document.querySelector('meta[name="_csrf"]');
    return m ? m.content : '';
}
function getCsrfHeader() {
    var m = document.querySelector('meta[name="_csrf_header"]');
    return m ? m.content : 'X-CSRF-TOKEN';
}
function ntFetch(url, opts) {
    opts = opts || {};
    opts.headers = opts.headers || {};
    opts.headers[getCsrfHeader()] = getCsrf();
    return fetch(url, opts);
}

var valgtKjonn = '';


function velgKjonn(kjonn) {
    valgtKjonn = kjonn;
    document.getElementById('kjonnMann').classList.toggle('valgt', kjonn === 'Mann');
    document.getElementById('kjonnKvinne').classList.toggle('valgt', kjonn === 'Kvinne');
}

function fyllEksempel(tekst) {
    document.getElementById('malInput').value = tekst;
    document.getElementById('malInput').focus();
}

async function genererDagsmeny() {
    var maal = document.getElementById('malInput').value.trim();
    if (!maal) {
        document.getElementById('malInput').focus();
        return;
    }

    var payload = { maal: maal };
    if (valgtKjonn) payload.kjonn = valgtKjonn;
    // Kjønn leses fra valgtKjonn-variabelen (satt av kjønn-knappene)
    var vektEl     = document.getElementById('vektInput');
    var hoydeEl    = document.getElementById('hoydeInput');
    var alderEl    = document.getElementById('alderInput');
    var aktivitetEl = document.getElementById('aktivitetInput');
    var vekt     = vektEl     ? vektEl.value.trim()     : '';
    var hoyde    = hoydeEl    ? hoydeEl.value.trim()    : '';
    var alder    = alderEl    ? alderEl.value.trim()    : '';
    var aktivitet = aktivitetEl ? aktivitetEl.value      : '';
    if (vekt)      payload.vekt      = vekt;
    if (hoyde)     payload.hoyde     = hoyde;
    if (alder)     payload.alder     = alder;
    if (aktivitet) payload.aktivitet = aktivitet;

    var btn = document.getElementById('genererBtn');
    btn.disabled = true;
    btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Claude analyserer målet ditt...';

    var res = document.getElementById('ntResultat');
    res.style.display = 'block';
    res.innerHTML =
        '<div class="laster-spinner">' +
            '<i class="fa-solid fa-spinner fa-spin"></i>' +
            '<p>Lager skreddersydd dagsmeny...</p>' +
        '</div>';
    res.scrollIntoView({ behavior: 'smooth', block: 'start' });

    try {
        var resp = await ntFetch('/api/naeringstrener/generer', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!resp.ok) {
            res.innerHTML = '<div style="background:white;border-radius:16px;padding:24px;color:#dc2626;font-weight:600;">Serverfeil: ' + resp.status + ' ' + resp.statusText + '. Prøv å laste siden på nytt.</div>';
        } else {
            var data = await resp.json();
            if (data.feil) {
                res.innerHTML = '<div style="background:white;border-radius:16px;padding:24px;color:#dc2626;font-weight:600;">Feil: ' + data.feil + '</div>';
            } else {
                sisteDagsmeny = data;
                visDagsmeny(data);
            }
        }
    } catch (e) {
        res.innerHTML = '<div style="background:white;border-radius:16px;padding:24px;color:#dc2626;font-weight:600;">Feil: ' + e.message + '</div>';
    }

    btn.disabled = false;
    btn.innerHTML = '<i class="fa-solid fa-dna"></i> Generer dagsmeny';
}

function visDagsmeny(data) {
    var res = document.getElementById('ntResultat');

    // Nærings-summary
    var summaryHtml =
        '<div class="nt-card" style="margin-bottom:0;">' +
            '<div class="nt-card-hode"><i class="fa-solid fa-chart-pie"></i> Dagens næringsinnhold</div>' +
            '<div class="nt-card-kropp">';

    if (data.tolkaMaal) {
        summaryHtml +=
            '<div class="tolka-maal"><strong>Tolket mål:</strong> ' + escHtml(data.tolkaMaal) + '</div>';
    }

    summaryHtml +=
        '<div class="naering-summary">' +
            '<div class="ns-boks ns-kcal">' +
                '<div class="ns-tall">' + (data.totalKcal || 0) + '</div>' +
                '<div class="ns-enhet">kcal</div>' +
                '<div class="ns-label">Kalorier</div>' +
            '</div>' +
            '<div class="ns-boks ns-prot">' +
                '<div class="ns-tall">' + (data.totalProtein || 0) + 'g</div>' +
                '<div class="ns-enhet">protein</div>' +
                '<div class="ns-label">Protein</div>' +
            '</div>' +
            '<div class="ns-boks ns-karbo">' +
                '<div class="ns-tall">' + (data.totalKarbo || 0) + 'g</div>' +
                '<div class="ns-enhet">karbo</div>' +
                '<div class="ns-label">Karbohydrat</div>' +
            '</div>' +
            '<div class="ns-boks ns-fett">' +
                '<div class="ns-tall">' + (data.totalFett || 0) + 'g</div>' +
                '<div class="ns-enhet">fett</div>' +
                '<div class="ns-label">Fett</div>' +
            '</div>' +
        '</div>' +
        '</div></div>';

    // Måltider
    var maltiderHtml = '';
    (data.maltider || []).forEach(function(m, i) {
        maltiderHtml += lagMaltidKort(m, i);
    });

    res.innerHTML = summaryHtml + '<div style="margin-top:16px;">' + maltiderHtml + '</div>';

    // Fest toggle-handlers
    res.querySelectorAll('.maltid-topp').forEach(function(el) {
        el.addEventListener('click', function() {
            var idx = el.getAttribute('data-idx');
            toggleOppskrift(idx);
        });
    });

    // Lagre-knapper
    res.querySelectorAll('.lagre-btn').forEach(function(btn) {
        btn.addEventListener('click', function() {
            var idx = parseInt(btn.getAttribute('data-idx'));
            lagreMaltid(idx);
        });
    });

    // Logg måltid-knapper (til næringstracker)
    res.querySelectorAll('.logg-maltid-btn').forEach(function(btn) {
        btn.addEventListener('click', function() {
            var idx = parseInt(btn.getAttribute('data-idx'));
            loggFraDagsmeny(idx, btn);
        });
    });
}

function lagMaltidKort(m, i) {
    var ikon = m.ikon || '🍽';
    return '<div class="maltid-kort" id="maltid-' + i + '">' +
        '<div class="maltid-topp" data-idx="' + i + '">' +
            '<div class="maltid-ikon">' + ikon + '</div>' +
            '<div class="maltid-info">' +
                '<div class="maltid-type">' + escHtml(m.type || '') + '</div>' +
                '<div class="maltid-navn">' + escHtml(m.navn || '') + '</div>' +
                '<div class="naering-badges">' +
                    '<span class="nb nb-kcal">' + (m.kcal || 0) + ' kcal</span>' +
                    '<span class="nb nb-prot">' + (m.protein || 0) + 'g protein</span>' +
                    '<span class="nb nb-karbo">' + (m.karbo || 0) + 'g karbo</span>' +
                    '<span class="nb nb-fett">' + (m.fett || 0) + 'g fett</span>' +
                '</div>' +
            '</div>' +
            '<i class="fa-solid fa-chevron-down maltid-pil" id="pil-' + i + '"></i>' +
        '</div>' +
        '<div class="maltid-oppskrift" id="opp-' + i + '">' +
            '<div class="opp-seksjon">' +
                '<div class="opp-seksjon-tittel">Ingredienser</div>' +
                '<div class="opp-tekst">' + escHtml(m.ingredienser || '') + '</div>' +
            '</div>' +
            '<div class="opp-seksjon">' +
                '<div class="opp-seksjon-tittel">Fremgangsmåte</div>' +
                '<div class="opp-tekst">' + escHtml(m.fremgangsmate || '') + '</div>' +
            '</div>' +
            '<div class="maltid-knapper">' +
                '<button class="lagre-btn" data-idx="' + i + '">+ Lagre i kokebok</button>' +
                '<button class="logg-maltid-btn" data-idx="' + i + '"><i class="fa-solid fa-utensils"></i> Logg dette måltidet</button>' +
            '</div>' +
        '</div>' +
    '</div>';
}

function toggleOppskrift(idx) {
    var opp = document.getElementById('opp-' + idx);
    var pil = document.getElementById('pil-' + idx);
    if (!opp) return;
    var aapen = opp.classList.toggle('vis');
    if (pil) pil.classList.toggle('aapen', aapen);
}

function lagreMaltid(idx) {
    if (!sisteDagsmeny || !sisteDagsmeny.maltider) return;
    var m = sisteDagsmeny.maltider[idx];
    if (!m) return;
    var form = document.createElement('form');
    form.method = 'POST'; form.action = '/lagre';
    var csrf = document.querySelector('meta[name="_csrf"]');
    if (csrf) {
        var ci = document.createElement('input');
        ci.type = 'hidden'; ci.name = '_csrf'; ci.value = csrf.content;
        form.appendChild(ci);
    }
    [['tittel', m.navn || ''],
     ['ingredienser', m.ingredienser || ''],
     ['fremgangsmate', m.fremgangsmate || ''],
     ['porsjoner', '1'],
     ['erOffentlig', 'false']].forEach(function(f) {
        var inp = document.createElement('input');
        inp.type = 'hidden'; inp.name = f[0]; inp.value = f[1];
        form.appendChild(inp);
    });
    document.body.appendChild(form);
    form.submit();
}

function escHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

/* ══════════════════════════════════════════════════════════
   NÆRINGSTRACKER — mål, logging, historikk og streaks
   Lagres lokalt i nettleseren (localStorage).
══════════════════════════════════════════════════════════ */
var NT_MAAL_KEY = 'mm-nt-maal';
var NT_LOGG_KEY = 'mm-nt-logg';
var NT_STANDARD_MAAL = { kcal: 2000, protein: 100, karbo: 250, fett: 70 };

function ntDatoKey(d) {
    var dt = d || new Date();
    return dt.getFullYear() + '-' +
        String(dt.getMonth() + 1).padStart(2, '0') + '-' +
        String(dt.getDate()).padStart(2, '0');
}

function hentMaal() {
    try {
        var m = JSON.parse(localStorage.getItem(NT_MAAL_KEY));
        if (m && m.kcal) return m;
    } catch (e) {}
    return Object.assign({}, NT_STANDARD_MAAL);
}

function hentLogg() {
    try {
        var l = JSON.parse(localStorage.getItem(NT_LOGG_KEY));
        if (l && typeof l === 'object') return l;
    } catch (e) {}
    return {};
}

function lagreLogg(logg) {
    // Rydd bort oppføringer eldre enn 30 dager
    var grense = new Date();
    grense.setDate(grense.getDate() - 30);
    var grenseKey = ntDatoKey(grense);
    Object.keys(logg).forEach(function(k) {
        if (k < grenseKey) delete logg[k];
    });
    try { localStorage.setItem(NT_LOGG_KEY, JSON.stringify(logg)); } catch (e) {}
}

function dagsSum(entries) {
    var sum = { kcal: 0, protein: 0, karbo: 0, fett: 0 };
    (entries || []).forEach(function(e) {
        sum.kcal    += e.kcal    || 0;
        sum.protein += e.protein || 0;
        sum.karbo   += e.karbo   || 0;
        sum.fett    += e.fett    || 0;
    });
    return sum;
}

/* Fargekoding: grønn = under mål, gul = nær mål, rød = over mål */
function makroStatus(verdi, maal) {
    if (!maal || maal <= 0) return 'under';
    var pct = verdi / maal;
    if (pct > 1.0)  return 'over';
    if (pct >= 0.85) return 'naer';
    return 'under';
}

/* ── MÅL-EDITOR ─────────────────────────────────────────── */
function toggleMaalEditor() {
    var ed = document.getElementById('maalEditor');
    if (!ed) return;
    var vis = ed.classList.toggle('vis');
    if (vis) {
        var m = hentMaal();
        document.getElementById('inpMaalKcal').value    = m.kcal;
        document.getElementById('inpMaalProtein').value = m.protein;
        document.getElementById('inpMaalKarbo').value   = m.karbo;
        document.getElementById('inpMaalFett').value    = m.fett;
    }
}

function lagreMaal() {
    var m = {
        kcal:    parseInt(document.getElementById('inpMaalKcal').value, 10)    || NT_STANDARD_MAAL.kcal,
        protein: parseInt(document.getElementById('inpMaalProtein').value, 10) || NT_STANDARD_MAAL.protein,
        karbo:   parseInt(document.getElementById('inpMaalKarbo').value, 10)   || NT_STANDARD_MAAL.karbo,
        fett:    parseInt(document.getElementById('inpMaalFett').value, 10)    || NT_STANDARD_MAAL.fett
    };
    try { localStorage.setItem(NT_MAAL_KEY, JSON.stringify(m)); } catch (e) {}
    document.getElementById('maalEditor').classList.remove('vis');
    renderTracker();
    if (typeof showToast === 'function') showToast('Målene dine er lagret! 🎯');
}

/* ── LOGGING ────────────────────────────────────────────── */
function fyllLoggNavn(tittel) {
    var inp = document.getElementById('loggNavn');
    if (!inp) return;
    inp.value = tittel;
    var kcal = document.getElementById('loggKcal');
    if (kcal) kcal.focus();
}

function loggMaltidSkjema() {
    var navn = (document.getElementById('loggNavn').value || '').trim();
    var kcal = parseInt(document.getElementById('loggKcal').value, 10) || 0;
    if (!navn) { document.getElementById('loggNavn').focus(); return; }
    if (kcal <= 0) { document.getElementById('loggKcal').focus(); return; }
    loggEntry({
        navn: navn,
        kcal: kcal,
        protein: parseInt(document.getElementById('loggProtein').value, 10) || 0,
        karbo:   parseInt(document.getElementById('loggKarbo').value, 10)   || 0,
        fett:    parseInt(document.getElementById('loggFett').value, 10)    || 0
    });
    ['loggNavn','loggKcal','loggProtein','loggKarbo','loggFett'].forEach(function(id) {
        document.getElementById(id).value = '';
    });
}

function loggFraDagsmeny(idx, btn) {
    if (!sisteDagsmeny || !sisteDagsmeny.maltider) return;
    var m = sisteDagsmeny.maltider[idx];
    if (!m) return;
    loggEntry({
        navn: m.navn || 'Måltid',
        kcal:    m.kcal    || 0,
        protein: m.protein || 0,
        karbo:   m.karbo   || 0,
        fett:    m.fett    || 0
    });
    if (btn) {
        btn.innerHTML = '<i class="fa-solid fa-check"></i> Logget!';
        btn.disabled = true;
    }
}

function loggEntry(entry) {
    var logg = hentLogg();
    var key = ntDatoKey();
    if (!logg[key]) logg[key] = [];
    entry.ts = Date.now();
    logg[key].push(entry);
    lagreLogg(logg);
    renderTracker();
    if (typeof showToast === 'function') showToast('Måltid logget: ' + entry.navn);
}

function slettLoggEntry(idx) {
    var logg = hentLogg();
    var key = ntDatoKey();
    if (!logg[key]) return;
    logg[key].splice(idx, 1);
    if (logg[key].length === 0) delete logg[key];
    lagreLogg(logg);
    renderTracker();
}

/* ── STREAK ─────────────────────────────────────────────── */
function beregnStreak(logg) {
    var streak = 0;
    var d = new Date();
    // Hvis ingenting er logget i dag, kan streaken fortsatt leve fra i går
    if (!logg[ntDatoKey(d)] || logg[ntDatoKey(d)].length === 0) {
        d.setDate(d.getDate() - 1);
    }
    while (logg[ntDatoKey(d)] && logg[ntDatoKey(d)].length > 0) {
        streak++;
        d.setDate(d.getDate() - 1);
    }
    return streak;
}

function motivasjonsMelding(streak, harLoggetIDag, kcalStatus) {
    if (!harLoggetIDag && streak === 0) return 'Logg ditt første måltid for å komme i gang!';
    if (!harLoggetIDag) return 'Logg et måltid i dag for å holde streaken i live! 🔥';
    if (kcalStatus === 'over') return 'Litt over kalorimålet i dag — i morgen er en ny dag! 🌱';
    if (kcalStatus === 'naer') return 'Nesten i mål — hold deg til planen! 💪';
    if (streak >= 7) return 'En hel uke på rad — imponerende! Du er på rett spor! 🎯';
    if (streak >= 3) return 'Sterk innsats flere dager på rad — du er på rett spor! 🎯';
    return 'Du er på rett spor! 🎯';
}

/* ── RENDER ─────────────────────────────────────────────── */
function renderTracker() {
    // Kjør kun på sider som har dashboardet
    if (!document.getElementById('vKcal')) return;

    var maal = hentMaal();
    var logg = hentLogg();
    var iDagKey = ntDatoKey();
    var dagens = logg[iDagKey] || [];
    var sum = dagsSum(dagens);

    // Makro-tiles
    var makroer = [
        { id: 'Kcal',    verdi: sum.kcal,    maal: maal.kcal    },
        { id: 'Protein', verdi: sum.protein, maal: maal.protein },
        { id: 'Karbo',   verdi: sum.karbo,   maal: maal.karbo   },
        { id: 'Fett',    verdi: sum.fett,    maal: maal.fett    }
    ];
    makroer.forEach(function(mk) {
        document.getElementById('v' + mk.id).textContent = mk.verdi;
        document.getElementById('m' + mk.id).textContent = mk.maal;
        var pct = mk.maal > 0 ? Math.min(100, Math.round(mk.verdi / mk.maal * 100)) : 0;
        document.getElementById('bar' + mk.id).style.width = pct + '%';
        var tile = document.getElementById('tile' + mk.id);
        tile.classList.remove('status-under', 'status-naer', 'status-over');
        tile.classList.add('status-' + makroStatus(mk.verdi, mk.maal));
    });

    // Streak + melding
    var streak = beregnStreak(logg);
    var harLoggetIDag = dagens.length > 0;
    document.getElementById('streakTall').textContent =
        streak === 1 ? '1 dag på rad' : streak + ' dager på rad';
    document.getElementById('streakMelding').textContent =
        motivasjonsMelding(streak, harLoggetIDag, makroStatus(sum.kcal, maal.kcal));

    // Dagens logg-liste
    var liste = document.getElementById('loggListe');
    if (liste) {
        if (dagens.length === 0) {
            liste.innerHTML = '<div class="logg-tom">Ingen måltider logget i dag ennå.</div>';
        } else {
            liste.innerHTML = dagens.map(function(e, i) {
                return '<div class="logg-rad">' +
                    '<span class="logg-rad-navn">' + escHtml(e.navn) + '</span>' +
                    '<span class="logg-rad-makro">' + (e.kcal || 0) + ' kcal · ' +
                        (e.protein || 0) + 'p / ' + (e.karbo || 0) + 'k / ' + (e.fett || 0) + 'f</span>' +
                    '<button type="button" class="logg-rad-slett" title="Slett" onclick="slettLoggEntry(' + i + ')">' +
                        '<i class="fa-solid fa-xmark"></i></button>' +
                '</div>';
            }).join('');
        }
    }

    renderHistorikk(logg, maal);
}

function renderHistorikk(logg, maal) {
    var chart = document.getElementById('histChart');
    if (!chart) return;

    var DAG_BOKSTAV = ['Man','Tir','Ons','Tor','Fre','Lør','Søn'];
    var dager = [];
    var maksKcal = maal.kcal;
    for (var i = 6; i >= 0; i--) {
        var d = new Date();
        d.setDate(d.getDate() - i);
        var sum = dagsSum(logg[ntDatoKey(d)]);
        if (sum.kcal > maksKcal) maksKcal = sum.kcal;
        dager.push({
            navn: DAG_BOKSTAV[(d.getDay() + 6) % 7],
            sum: sum,
            erIDag: i === 0,
            harData: !!(logg[ntDatoKey(d)] && logg[ntDatoKey(d)].length)
        });
    }
    var chartMax = maksKcal * 1.15;
    var barMaxPx = 96;   // maks søylehøyde i px
    var bunnPx   = 22;   // plass til dag-label under søylene

    var html = '';
    // Mål-linje (stiplet)
    var maalPos = bunnPx + Math.round(maal.kcal / chartMax * barMaxPx);
    html += '<div class="hist-maal-linje" style="bottom:' + maalPos + 'px;">' +
        '<span class="hist-maal-label">Mål ' + maal.kcal + '</span></div>';

    dager.forEach(function(dag) {
        var h = Math.max(3, Math.round(dag.sum.kcal / chartMax * barMaxPx));
        var status = dag.harData ? makroStatus(dag.sum.kcal, maal.kcal) : 'tom';
        var klasse = status === 'over' ? ' over' : (status === 'naer' ? ' naer' : (status === 'tom' ? ' tom' : ''));
        html += '<div class="hist-kol">' +
            '<div class="hist-bar' + klasse + '" style="height:' + h + 'px;" title="' + dag.sum.kcal + ' kcal">' +
                (dag.harData ? '<span class="hist-verdi">' + dag.sum.kcal + '</span>' : '') +
            '</div>' +
            '<span class="hist-dag' + (dag.erIDag ? ' idag' : '') + '">' + dag.navn + '</span>' +
        '</div>';
    });
    chart.innerHTML = html;

    // Ukessnitt (over dager med logget data)
    var dagerMedData = dager.filter(function(d) { return d.harData; });
    var n = dagerMedData.length || 1;
    var tot = { kcal: 0, protein: 0, karbo: 0, fett: 0 };
    dagerMedData.forEach(function(d) {
        tot.kcal += d.sum.kcal; tot.protein += d.sum.protein;
        tot.karbo += d.sum.karbo; tot.fett += d.sum.fett;
    });
    document.getElementById('snittKcal').textContent    = Math.round(tot.kcal / n);
    document.getElementById('snittProtein').textContent = Math.round(tot.protein / n) + ' g';
    document.getElementById('snittKarbo').textContent   = Math.round(tot.karbo / n) + ' g';
    document.getElementById('snittFett').textContent    = Math.round(tot.fett / n) + ' g';
}

document.addEventListener('DOMContentLoaded', function() {
    renderTracker();
    // Enter i logg-skjemaet = logg måltid
    var loggNavn = document.getElementById('loggNavn');
    if (loggNavn) {
        ['loggNavn','loggKcal','loggProtein','loggKarbo','loggFett'].forEach(function(id) {
            var el = document.getElementById(id);
            if (el) el.addEventListener('keydown', function(e) {
                if (e.key === 'Enter') { e.preventDefault(); loggMaltidSkjema(); }
            });
        });
    }
});
