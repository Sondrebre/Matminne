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
            '<button class="lagre-btn" data-idx="' + i + '">+ Lagre i kokebok</button>' +
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
