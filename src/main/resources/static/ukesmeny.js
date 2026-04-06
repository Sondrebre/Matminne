function byttDag(knapp) {
    document.querySelectorAll('.dag-tab').forEach(function(t) { t.classList.remove('aktiv'); });
    document.querySelectorAll('.dag-panel').forEach(function(p) { p.classList.remove('aktiv'); });
    knapp.classList.add('aktiv');
    document.getElementById('panel-' + knapp.dataset.idx).classList.add('aktiv');
}

var aktivTittel = '';
var aktivPorsjoner = 2;

function apneModal(tittel) {
    aktivTittel = tittel;
    aktivPorsjoner = 2;
    document.getElementById('modalTittel').textContent = tittel;
    document.getElementById('porsjonTall').textContent = 2;
    document.getElementById('modalResultat').innerHTML = '';
    document.getElementById('oppskriftModal').classList.add('vis');
    document.body.style.overflow = 'hidden';
}

function lukkModal() {
    document.getElementById('oppskriftModal').classList.remove('vis');
    document.body.style.overflow = '';
}

function lukkModalHvis(e) {
    if (e.target === document.getElementById('oppskriftModal')) lukkModal();
}

function endrePorsjoner(delta) {
    aktivPorsjoner = Math.max(1, Math.min(20, aktivPorsjoner + delta));
    document.getElementById('porsjonTall').textContent = aktivPorsjoner;
    document.getElementById('modalResultat').innerHTML = '';
}

function getCsrf() {
    var meta = document.querySelector('meta[name="_csrf"]');
    return meta ? meta.content : '';
}
function getCsrfHeader() {
    var meta = document.querySelector('meta[name="_csrf_header"]');
    return meta ? meta.content : 'X-CSRF-TOKEN';
}
function csrfPost(url, body, contentType) {
    var headers = { 'Content-Type': contentType || 'application/json' };
    headers[getCsrfHeader()] = getCsrf();
    return fetch(url, { method: 'POST', headers: headers, body: body });
}

async function hentOppskrift() {
    var btn = document.getElementById('genererBtn');
    btn.disabled = true;
    btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Genererer...';
    document.getElementById('modalResultat').innerHTML =
        '<div class="modal-loading"><i class="fa-solid fa-spinner fa-spin" style="font-size:1.5rem;color:#22c55e;"></i><br><br>Claude lager oppskrift...</div>';

    try {
        var res = await csrfPost('/api/ukesmeny/oppskrift',
            JSON.stringify({ tittel: aktivTittel, porsjoner: String(aktivPorsjoner) }),
            'application/json');
        var data = await res.json();

        if (data.feil) {
            document.getElementById('modalResultat').innerHTML =
                '<div class="modal-feil">Feil: ' + data.feil + '</div>';
        } else {
            var porsjonTekst = 'Ingredienser \u2014 ' + aktivPorsjoner + ' porsjon' + (aktivPorsjoner !== 1 ? 'er' : '');
            document.getElementById('modalResultat').innerHTML =
                '<div class="modal-seksjon">' +
                    '<div class="modal-seksjon-tittel">' + porsjonTekst + '</div>' +
                    '<div class="modal-tekst">' + data.ingredienser + '</div>' +
                '</div>' +
                '<div class="modal-seksjon">' +
                    '<div class="modal-seksjon-tittel">Fremgangsm\u00E5te</div>' +
                    '<div class="modal-tekst">' + data.fremgangsmate + '</div>' +
                '</div>' +
                '<div class="lagre-rad">' +
                    '<button class="lagre-btn" onclick="lagreOppskrift()">' +
                        '<i class="fa-solid fa-plus"></i> Lagre i kokebok' +
                    '</button>' +
                    '<button class="lagre-btn" onclick="leggTilHandelListe()">' +
                        '<i class="fa-solid fa-cart-shopping"></i> Til handleliste' +
                    '</button>' +
                '</div>';
        }
    } catch (e) {
        document.getElementById('modalResultat').innerHTML =
            '<div class="modal-feil">Kunne ikke kontakte serveren. Pr\u00F8v igjen.</div>';
    }

    btn.disabled = false;
    btn.innerHTML = '<i class="fa-solid fa-wand-magic-sparkles"></i> Generer p\u00E5 nytt';
}

function lagreOppskrift() {
    var ing = document.querySelector('.modal-seksjon:nth-child(1) .modal-tekst');
    var frem = document.querySelector('.modal-seksjon:nth-child(2) .modal-tekst');
    var ingTekst = ing ? ing.textContent : '';
    var fremTekst = frem ? frem.textContent : '';
    var form = document.createElement('form');
    form.method = 'POST';
    form.action = '/lagre';
    var csrf = document.querySelector('meta[name="_csrf"]');
    if (csrf) {
        var ci = document.createElement('input');
        ci.type = 'hidden'; ci.name = '_csrf'; ci.value = csrf.content;
        form.appendChild(ci);
    }
    var felt = [['tittel', aktivTittel], ['ingredienser', ingTekst], ['fremgangsmate', fremTekst],
                ['porsjoner', aktivPorsjoner], ['erOffentlig', 'false']];
    felt.forEach(function(f) {
        var inp = document.createElement('input');
        inp.type = 'hidden'; inp.name = f[0]; inp.value = f[1];
        form.appendChild(inp);
    });
    document.body.appendChild(form);
    form.submit();
}

async function leggTilHandelListe() {
    var ing = document.querySelector('.modal-seksjon:nth-child(1) .modal-tekst');
    var tekst = ing ? ing.textContent : '';
    var linjer = tekst.split('\n').filter(function(l) { return l.trim().startsWith('-'); });
    for (var i = 0; i < linjer.length; i++) {
        await csrfPost('/handleliste/legg-til',
            'tekst=' + encodeURIComponent(linjer[i].replace(/^-\s*/, '').trim()),
            'application/x-www-form-urlencoded');
    }
    lukkModal();
    window.location.href = '/handleliste';
}

document.addEventListener('keydown', function(e) { if (e.key === 'Escape') lukkModal(); });
