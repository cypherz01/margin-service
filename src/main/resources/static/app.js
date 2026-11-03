'use strict';

const API_URL = '/api/v1/margin/calculations';
const QUOTE_URL = '/api/v1/quotes';

const form = document.getElementById('calc-form');
const positionsBody = document.getElementById('positions');
const collateralBody = document.getElementById('collateral');
const submitButton = document.getElementById('submit');

const emptyView = document.getElementById('empty');
const errorView = document.getElementById('error');
const resultView = document.getElementById('result');

const plainMoney = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Formats in the given ISO 4217 currency, using its own minor unit (e.g. 0 decimals for JPY). */
function moneyIn(currency) {
    try {
        return new Intl.NumberFormat(undefined, { style: 'currency', currency });
    } catch {
        return plainMoney;
    }
}

const EXAMPLE = {
    accountId: 'ACC-1',
    currency: 'USD',
    positions: [
        { instrumentId: 'AAPL', assetClass: 'EQUITY', quantity: 100, price: 50 },
        { instrumentId: 'UST-10Y', assetClass: 'BOND', quantity: 50, price: 98.5 },
        { instrumentId: 'EURUSD', assetClass: 'FX', quantity: -10000, price: 1.08 },
    ],
    collateral: [
        { type: 'CASH', marketValue: 800 },
        { type: 'GOVERNMENT_BOND', marketValue: 400 },
    ],
};

// --- Row management -------------------------------------------------------

function addRow(kind, values = {}) {
    const body = kind === 'position' ? positionsBody : collateralBody;
    const row = document.getElementById(`${kind}-row`).content.firstElementChild.cloneNode(true);
    for (const [name, value] of Object.entries(values)) {
        const field = row.querySelector(`[name="${name}"]`);
        if (field) field.value = value;
    }
    body.appendChild(row);
    if (kind === 'position') syncQuoteButton(row);
    return row;
}

/** Live prices come from an equity quote feed, so the lookup is only offered for equity rows. */
function syncQuoteButton(row) {
    const isEquity = row.querySelector('[name=assetClass]').value === 'EQUITY';
    const button = row.querySelector('button.quote');
    button.disabled = !isEquity;
    button.title = isEquity ? 'Get live share price' : 'Live prices are available for equities only';
}

function clearRows() {
    positionsBody.replaceChildren();
    collateralBody.replaceChildren();
}

document.querySelectorAll('[data-add]').forEach(button =>
    button.addEventListener('click', () => addRow(button.dataset.add).querySelector('input, select').focus()));

form.addEventListener('click', event => {
    if (event.target.classList.contains('remove')) {
        event.target.closest('tr').remove();
    } else if (event.target.classList.contains('quote')) {
        fetchQuote(event.target.closest('tr'));
    }
});

form.addEventListener('change', event => {
    if (event.target.name === 'assetClass') syncQuoteButton(event.target.closest('tr'));
    if (event.target.name === 'currency') flagLivePricesInOtherCurrencies();
});

// Typing a price by hand replaces any live price, so it no longer carries the quote's currency
form.addEventListener('input', event => {
    if (event.target.name === 'price' && event.target.dataset.quoteCurrency) {
        delete event.target.dataset.quoteCurrency;
        flagLivePricesInOtherCurrencies(); // clears this field's flag, and the warning once none remain
    }
});

// --- Live prices ----------------------------------------------------------

const quoteStatus = document.getElementById('quote-status');

function showQuoteStatus(message, isError) {
    quoteStatus.textContent = message;
    quoteStatus.classList.toggle('error', isError);
    quoteStatus.hidden = false;
}

async function fetchQuote(row) {
    const symbolInput = row.querySelector('[name=instrumentId]');
    const priceInput = row.querySelector('[name=price]');
    const button = row.querySelector('button.quote');
    const symbol = symbolInput.value.trim();
    if (!symbol) {
        showQuoteStatus('Enter a ticker symbol in the Instrument column first.', true);
        symbolInput.focus();
        return;
    }

    button.disabled = true;
    button.classList.add('loading');
    try {
        const response = await fetch(`${QUOTE_URL}/${encodeURIComponent(symbol)}`, {
            headers: { Accept: 'application/json, application/problem+json' },
        });
        const body = await response.json().catch(() => null);
        if (response.ok) {
            const asOf = new Date(body.asOf).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
            const quoted = `${moneyIn(body.currency).format(body.price)} as of ${asOf}`;
            const portfolioCurrency = form.currency.value;
            if (body.currency !== portfolioCurrency) {
                // The service does no FX conversion, so a price in another currency would be silently wrong
                showQuoteStatus(`${body.symbol} is quoted in ${body.currency} (${quoted}), but this portfolio is in `
                    + `${portfolioCurrency}. Switch the currency to ${body.currency}, or enter a `
                    + `${portfolioCurrency} price manually.`, true);
                return;
            }
            priceInput.value = body.price;
            priceInput.dataset.quoteCurrency = body.currency;
            priceInput.setCustomValidity('');
            priceInput.classList.remove('invalid');
            priceInput.title = `Live price for ${body.symbol} as of ${asOf}`;
            showQuoteStatus(`${body.symbol}: ${quoted}`, false);
        } else if (response.status === 404) {
            showQuoteStatus(`No price found for "${symbol}". Check the ticker, or enter the price manually.`, true);
        } else if (response.status === 400) {
            showQuoteStatus(`"${symbol}" is not a valid ticker symbol.`, true);
        } else {
            showQuoteStatus(body?.detail ?? 'Live prices are unavailable right now; enter the price manually.', true);
        }
    } catch (err) {
        showQuoteStatus('Cannot reach the margin service.', true);
    } finally {
        button.classList.remove('loading');
        syncQuoteButton(row);
    }
}

/** After a currency switch, live prices fetched in the old currency must be re-entered before calculating. */
function flagLivePricesInOtherCurrencies() {
    const portfolioCurrency = form.currency.value;
    const stale = [];
    positionsBody.querySelectorAll('[name=price]').forEach(priceInput => {
        const quoteCurrency = priceInput.dataset.quoteCurrency;
        const mismatch = Boolean(quoteCurrency) && quoteCurrency !== portfolioCurrency;
        const symbol = priceInput.closest('tr').querySelector('[name=instrumentId]').value.trim();
        priceInput.setCustomValidity(mismatch
            ? `The price for ${symbol} is a live ${quoteCurrency} price, but the portfolio is in ${portfolioCurrency}.`
            : '');
        priceInput.classList.toggle('invalid', mismatch);
        if (mismatch) stale.push(symbol);
    });
    if (stale.length) {
        showQuoteStatus(`${stale.join(', ')}: live price in another currency. Switch back, or re-enter the `
            + `price in ${portfolioCurrency}.`, true);
    } else if (!quoteStatus.hidden && quoteStatus.classList.contains('error')) {
        quoteStatus.hidden = true;
    }
}

document.getElementById('load-example').addEventListener('click', () => {
    clearRows();
    form.accountId.value = EXAMPLE.accountId;
    form.currency.value = EXAMPLE.currency;
    EXAMPLE.positions.forEach(p => addRow('position', p));
    EXAMPLE.collateral.forEach(c => addRow('collateral', c));
});

// --- Request building -----------------------------------------------------

function readRows(body, numericFields) {
    return [...body.querySelectorAll('tr')].map(row => {
        const entry = {};
        row.querySelectorAll('input, select').forEach(field => {
            entry[field.name] = numericFields.includes(field.name) ? Number(field.value) : field.value.trim();
        });
        return entry;
    });
}

/**
 * Browser-side checks only; the server remains the source of truth for validation.
 * Highlights invalid fields and returns one message per distinct problem (empty when valid).
 */
function validate() {
    const problems = new Set();
    form.querySelectorAll('input').forEach(input => {
        const ok = input.checkValidity();
        input.classList.toggle('invalid', !ok);
        if (!ok) problems.add(problemFor(input));
    });
    return [...problems];
}

function problemFor(input) {
    const { validity } = input;
    if (validity.customError) return input.validationMessage;
    switch (input.name) {
        case 'accountId':
            return validity.valueMissing
                ? 'Enter an account ID.'
                : "Account ID can only contain letters, digits, '.', '_', ':' or '-' (up to 64 characters, no spaces).";
        case 'instrumentId':
            return 'Every position needs an instrument.';
        case 'quantity':
            return 'Every position needs a quantity.';
        case 'price':
            return validity.rangeUnderflow ? 'Prices cannot be negative.' : 'Every position needs a price.';
        case 'marketValue':
            return validity.rangeUnderflow
                ? 'Collateral market values cannot be negative.'
                : 'Every collateral item needs a market value.';
        default:
            return input.validationMessage;
    }
}

function buildRequest() {
    return {
        accountId: form.accountId.value.trim(),
        currency: form.currency.value,
        positions: readRows(positionsBody, ['quantity', 'price']),
        collateral: readRows(collateralBody, ['marketValue']),
    };
}

// --- Submission -----------------------------------------------------------

form.addEventListener('submit', async event => {
    event.preventDefault();
    const problems = validate();
    if (problems.length === 1) {
        showError('Check the highlighted field', problems[0]);
        return;
    }
    if (problems.length > 1) {
        showError('Check the highlighted fields', '', problems);
        return;
    }

    submitButton.disabled = true;
    submitButton.textContent = 'Calculating…';
    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json, application/problem+json' },
            body: JSON.stringify(buildRequest()),
        });
        const body = await response.json().catch(() => null);
        if (response.ok) {
            showResult(body);
        } else {
            showProblem(response.status, body);
        }
    } catch (err) {
        showError('Cannot reach the margin service', 'Check that the service is running and try again.');
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = 'Calculate';
    }
});

// --- Rendering ------------------------------------------------------------

function showOnly(view) {
    for (const v of [emptyView, errorView, resultView]) v.hidden = v !== view;
}

function showError(title, detail, items = []) {
    errorView.replaceChildren();
    const heading = document.createElement('strong');
    heading.textContent = title;
    errorView.append(heading, document.createTextNode(detail ?? ''));
    if (items.length) {
        const list = document.createElement('ul');
        items.forEach(text => {
            const li = document.createElement('li');
            li.textContent = text;
            list.appendChild(li);
        });
        errorView.appendChild(list);
    }
    showOnly(errorView);
}

/** Renders an RFC 7807 problem response from the API. */
function showProblem(status, problem) {
    const detail = problem?.detail ?? '';
    switch (status) {
        case 503:
            showError('Margin call could not be raised',
                'A margin call is required but could not be published, so no call has been issued. Retry shortly.');
            break;
        case 422:
            showError('Margin configuration missing', detail);
            break;
        case 400:
            showError('Invalid request', detail);
            break;
        default:
            showError(`Request failed (${status})`, detail || problem?.title || '');
    }
}

function showResult(result) {
    const money = moneyIn(result.currency);
    for (const key of ['grossExposure', 'initialMargin', 'maintenanceMargin', 'collateralValue', 'excess', 'marginCallAmount']) {
        document.getElementById(`m-${key}`).textContent = money.format(result[key]);
    }
    const excessCell = document.getElementById('m-excess');
    excessCell.classList.toggle('negative', result.excess < 0);
    excessCell.classList.toggle('positive', result.excess >= 0);

    const collateral = result.collateralValue;
    const mm = result.maintenanceMargin;
    const im = result.initialMargin;

    const status = document.getElementById('status');
    let level;
    if (result.marginCallRequired) {
        level = 'bad';
        status.className = 'banner banner-error';
        status.innerHTML = '<strong>Margin call issued</strong>';
        status.append(`Collateral is below maintenance margin. ${money.format(result.marginCallAmount)} is required to restore initial margin for ${result.accountId}.`);
    } else if (collateral < im) {
        level = 'warn';
        status.className = 'banner banner-warn';
        status.innerHTML = '<strong>Below initial margin</strong>';
        status.append('Collateral covers maintenance margin, so no call has been issued, but it is short of initial margin.');
    } else {
        level = 'ok';
        status.className = 'banner banner-ok';
        status.innerHTML = '<strong>Fully margined</strong>';
        status.append(`No margin call. Excess collateral of ${money.format(result.excess)}.`);
    }

    // Scale so the larger of collateral and IM sits at ~85% of the bar, leaving headroom.
    const scale = Math.max(collateral, im) / 0.85 || 1;
    const pct = value => `${Math.min(100, (value / scale) * 100)}%`;
    const fill = document.getElementById('coverage-fill');
    fill.className = `coverage-fill ${level}`;
    fill.style.width = pct(collateral);
    document.getElementById('marker-mm').style.left = pct(mm);
    document.getElementById('marker-im').style.left = pct(im);

    showOnly(resultView);
}

// Start with one empty row of each so the form is immediately usable.
addRow('position');
addRow('collateral');
