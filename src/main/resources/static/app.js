/**
 * ExpenseFlow v2.0 - Frontend Client Application
 * Multi-tenant JWT Authentication, Multi-Currency, Custom Categories & Chart.js
 */

const API_BASE = '/api/transactions';
const BUDGET_API = '/api/budget';
const AUTH_API = '/api/auth';
const CATEGORY_API = '/api/categories';

let authToken = localStorage.getItem('expense_token') || null;
let currentUser = JSON.parse(localStorage.getItem('expense_user') || 'null');
let userCurrency = (currentUser && currentUser.currency) || 'INR';

let currentPage = 0;
const pageSize = 10;
let totalPages = 0;

let monthlyChart = null;
let categoryChart = null;
let isRegisterMode = false;

// Currency mapping
const CURRENCY_CONFIG = {
    INR: { locale: 'en-IN', currency: 'INR', symbol: '₹' },
    USD: { locale: 'en-US', currency: 'USD', symbol: '$' },
    EUR: { locale: 'de-DE', currency: 'EUR', symbol: '€' },
    GBP: { locale: 'en-GB', currency: 'GBP', symbol: '£' }
};

// Initialize application
document.addEventListener('DOMContentLoaded', () => {
    handleOAuthRedirect();
    initDefaultDates();
    setupEventListeners();

    if (authToken && currentUser) {
        setLoggedInUI(true);
        loadDashboard();
    } else {
        setLoggedInUI(false);
    }
});

/**
 * Handle Google OAuth2 Redirect Callback
 */
function handleOAuthRedirect() {
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token');
    const error = urlParams.get('error');

    if (error) {
        showToast('Login Failed', decodeURIComponent(error), 'error');
        window.history.replaceState({}, document.title, window.location.pathname);
        return;
    }

    if (token) {
        authToken = token;
        currentUser = {
            id: Number(urlParams.get('userId')),
            name: decodeURIComponent(urlParams.get('name') || 'Google User'),
            email: decodeURIComponent(urlParams.get('email') || ''),
            currency: urlParams.get('currency') || 'INR'
        };
        userCurrency = currentUser.currency;

        localStorage.setItem('expense_token', authToken);
        localStorage.setItem('expense_user', JSON.stringify(currentUser));

        // Clean query parameters from URL bar
        window.history.replaceState({}, document.title, window.location.pathname);

        showToast('Signed In', `Welcome ${currentUser.name}!`, 'success');
    }
}

function initDefaultDates() {
    const today = new Date();
    const curYear = today.getFullYear();
    const curMonth = String(today.getMonth() + 1).padStart(2, '0');
    const curDay = String(today.getDate()).padStart(2, '0');

    const txDateInput = document.getElementById('txDate');
    if (txDateInput) txDateInput.value = `${curYear}-${curMonth}-${curDay}`;

    const budgetMonthInput = document.getElementById('budgetMonth');
    if (budgetMonthInput) budgetMonthInput.value = `${curYear}-${curMonth}`;
}

function setupEventListeners() {
    let searchDebounceTimer;
    const searchInput = document.getElementById('filterSearch');
    if (searchInput) {
        searchInput.addEventListener('input', () => {
            clearTimeout(searchDebounceTimer);
            searchDebounceTimer = setTimeout(() => {
                currentPage = 0;
                fetchTransactions();
            }, 350);
        });
    }

    ['filterType', 'filterCategory', 'filterStartDate', 'filterEndDate'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.addEventListener('change', async () => {
                if (id === 'filterCategory' && el.value === '__MANAGE__') {
                    openCategoryModal();
                    el.value = '';
                    return;
                }
                currentPage = 0;
                await loadDashboard();
            });
        }
    });

    const currencySelector = document.getElementById('userCurrencySelector');
    if (currencySelector) {
        currencySelector.addEventListener('change', async (e) => {
            const newCurrency = e.target.value;
            userCurrency = newCurrency;
            if (currentUser) {
                currentUser.currency = newCurrency;
                localStorage.setItem('expense_user', JSON.stringify(currentUser));
                try {
                    await authFetch('/api/auth/profile', {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            name: currentUser.name,
                            currency: newCurrency
                        })
                    });
                } catch (err) {
                    console.error('Failed to sync currency to server:', err);
                }
            }
            updateCurrencySymbols();
            await loadDashboard();
            showToast('Currency Updated', `Preferred currency set to ${newCurrency}.`, 'info');
        });
    }
}

function updateCurrencySymbols() {
    const symbol = (CURRENCY_CONFIG[userCurrency] || CURRENCY_CONFIG.INR).symbol;
    document.querySelectorAll('.currencySymbol').forEach(el => {
        el.textContent = symbol;
    });
    const currencySelector = document.getElementById('userCurrencySelector');
    if (currencySelector) {
        currencySelector.value = userCurrency;
    }
}

/**
 * Authorized Fetch Wrapper with strict no-cache to guarantee live auto-updates
 */
async function authFetch(url, options = {}) {
    options.headers = options.headers || {};
    if (authToken) {
        options.headers['Authorization'] = `Bearer ${authToken}`;
    }
    options.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate';
    options.headers['Pragma'] = 'no-cache';
    options.cache = 'no-store';

    const response = await fetch(url, options);

    if (response.status === 401 || response.status === 403) {
        logout(false);
        showAuthModal(false);
        throw new Error('Session expired. Please log in again.');
    }

    return response;
}

/**
 * UI Visibility Toggle for Auth State
 */
function setLoggedInUI(isLoggedIn) {
    const authHero = document.getElementById('authHeroSection');
    const dashboardMain = document.getElementById('dashboardMainSection');
    const userProfileNav = document.getElementById('userProfileNav');
    const currencyContainer = document.getElementById('currencySelectorContainer');
    const loginPromptBtn = document.getElementById('loginPromptBtn');

    if (isLoggedIn) {
        authHero.classList.add('hidden');
        dashboardMain.classList.remove('hidden');
        userProfileNav.classList.remove('hidden');
        userProfileNav.classList.add('flex');
        currencyContainer.classList.remove('hidden');
        currencyContainer.classList.add('flex');
        loginPromptBtn.classList.add('hidden');

        document.getElementById('navUserName').textContent = currentUser ? currentUser.name : 'User';
        userCurrency = (currentUser && currentUser.currency) || 'INR';
        updateCurrencySymbols();
        loadCategories();
    } else {
        authHero.classList.remove('hidden');
        dashboardMain.classList.add('hidden');
        userProfileNav.classList.add('hidden');
        currencyContainer.classList.add('hidden');
        loginPromptBtn.classList.remove('hidden');
    }
}

/**
 * Category Management
 */
let lastSelectedCategory = '';

async function loadCategories(selectedCategoryName = null) {
    try {
        const response = await authFetch(CATEGORY_API);
        if (!response.ok) return;
        const categories = await response.json();

        // Populate Transaction Modal dropdown
        const txCategory = document.getElementById('txCategory');
        const filterCategory = document.getElementById('filterCategory');

        const currentFilterVal = filterCategory.value;
        const currentTxVal = selectedCategoryName || txCategory.value;

        filterCategory.innerHTML = '<option value="">All Categories</option>';
        txCategory.innerHTML = '';

        categories.forEach(c => {
            const opt1 = document.createElement('option');
            opt1.value = c.name;
            opt1.textContent = c.name;
            filterCategory.appendChild(opt1);

            const opt2 = document.createElement('option');
            opt2.value = c.name;
            opt2.textContent = c.name;
            txCategory.appendChild(opt2);
        });

        // Add Option to create custom category directly from the transaction category list
        const customTxOpt = document.createElement('option');
        customTxOpt.value = '__NEW__';
        customTxOpt.textContent = '✨ + Add Custom Category...';
        customTxOpt.className = 'font-semibold text-emerald-600 bg-emerald-50';
        txCategory.appendChild(customTxOpt);

        // Add Option to manage categories from filter list
        const manageFilterOpt = document.createElement('option');
        manageFilterOpt.value = '__MANAGE__';
        manageFilterOpt.textContent = '⚙️ Manage / Add Categories...';
        manageFilterOpt.className = 'font-semibold text-emerald-600 bg-emerald-50';
        filterCategory.appendChild(manageFilterOpt);

        if (currentFilterVal && currentFilterVal !== '__MANAGE__') {
            filterCategory.value = currentFilterVal;
        }

        if (currentTxVal && currentTxVal !== '__NEW__') {
            txCategory.value = currentTxVal;
            lastSelectedCategory = currentTxVal;
        } else if (categories.length > 0) {
            txCategory.value = categories[0].name;
            lastSelectedCategory = categories[0].name;
        }

        // Populate Category Manager Modal List
        const modalList = document.getElementById('categoryModalList');
        if (modalList) {
            modalList.innerHTML = categories.map(c => `
                <div class="flex items-center justify-between p-2.5">
                    <div class="flex items-center space-x-2">
                        <span class="w-3 h-3 rounded-full" style="background-color: ${c.color}"></span>
                        <span class="font-medium text-slate-800">${escapeHtml(c.name)}</span>
                        <span class="text-[10px] uppercase font-bold px-1.5 py-0.5 rounded ${c.type === 'EXPENSE' ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'}">${c.type}</span>
                    </div>
                    ${c.custom ? `
                        <button onclick="deleteCustomCategory(${c.id})" class="text-slate-400 hover:text-rose-600 p-1" title="Delete Category">
                            <i class="fa-regular fa-trash-can"></i>
                        </button>
                    ` : '<span class="text-[10px] text-slate-400 font-semibold">Default</span>'}
                </div>
            `).join('');
        }

    } catch (err) {
        console.error('Failed to load categories:', err);
    }
}

function handleTxCategoryChange(selectEl) {
    if (selectEl.value === '__NEW__') {
        openQuickCategoryAdd();
        if (lastSelectedCategory && lastSelectedCategory !== '__NEW__') {
            selectEl.value = lastSelectedCategory;
        }
    } else {
        lastSelectedCategory = selectEl.value;
        cancelQuickCategory();
    }
}

function openQuickCategoryAdd() {
    const box = document.getElementById('quickCategoryBox');
    if (!box) return;
    box.classList.remove('hidden');
    const input = document.getElementById('quickCategoryName');
    input.value = '';
    const err = document.getElementById('quickCategoryError');
    if (err) err.classList.add('hidden');
    input.focus();
}

function toggleQuickCategoryAdd() {
    const box = document.getElementById('quickCategoryBox');
    if (!box) return;
    if (box.classList.contains('hidden')) {
        openQuickCategoryAdd();
    } else {
        cancelQuickCategory();
    }
}

function cancelQuickCategory() {
    const box = document.getElementById('quickCategoryBox');
    if (!box) return;
    box.classList.add('hidden');
    const err = document.getElementById('quickCategoryError');
    if (err) err.classList.add('hidden');
}

async function saveQuickCategory() {
    const input = document.getElementById('quickCategoryName');
    const name = input.value.trim();
    const err = document.getElementById('quickCategoryError');
    const btn = document.getElementById('quickCategorySaveBtn');

    if (!name) {
        if (err) {
            err.textContent = 'Please enter a category name.';
            err.classList.remove('hidden');
        }
        input.focus();
        return;
    }

    const type = document.getElementById('txType').value || 'EXPENSE';
    const payload = {
        name: name,
        type: type,
        color: type === 'INCOME' ? '#10b981' : '#f59e0b',
        icon: 'fa-tag'
    };

    btn.disabled = true;
    btn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin"></i>';

    try {
        const response = await authFetch(CATEGORY_API, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const errData = await response.json();
            throw new Error(errData.message || 'Failed to create category');
        }

        const newCat = await response.json();
        await loadCategories(newCat.name);
        lastSelectedCategory = newCat.name;
        cancelQuickCategory();
        showToast('Category Created', `Custom category '${newCat.name}' created and selected!`, 'success');
    } catch (e) {
        if (err) {
            err.textContent = e.message;
            err.classList.remove('hidden');
        } else {
            showToast('Error', e.message, 'error');
        }
    } finally {
        btn.disabled = false;
        btn.innerHTML = 'Add';
    }
}

function openCategoryModal() {
    loadCategories();
    document.getElementById('categoryModal').classList.remove('hidden');
}

function closeCategoryModal() {
    document.getElementById('categoryModal').classList.add('hidden');
}

async function handleCreateCategory(event) {
    event.preventDefault();
    const name = document.getElementById('newCategoryName').value.trim();
    const type = document.getElementById('newCategoryType').value;

    try {
        const response = await authFetch(CATEGORY_API, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, type })
        });

        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.message || 'Failed to add category');
        }

        document.getElementById('newCategoryName').value = '';
        showToast('Created', `Category '${name}' created!`, 'success');
        loadCategories();
    } catch (err) {
        showToast('Error', err.message, 'error');
    }
}

async function deleteCustomCategory(id) {
    if (!confirm('Are you sure you want to delete this custom category? Note: Existing recorded transactions will keep this category name.')) return;
    try {
        const response = await authFetch(`${CATEGORY_API}/${id}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('Cannot delete category');
        showToast('Deleted', 'Category removed.', 'info');
        await loadCategories();
        await loadDashboard();
    } catch (err) {
        showToast('Error', err.message, 'error');
    }
}

/**
 * Currency Switcher
 */
async function changeCurrency(newCurrency) {
    userCurrency = newCurrency;
    updateCurrencySymbols();

    if (currentUser) {
        currentUser.currency = newCurrency;
        localStorage.setItem('expense_user', JSON.stringify(currentUser));

        try {
            await authFetch(`${AUTH_API}/profile`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: currentUser.name, currency: newCurrency })
            });
            showToast('Currency Updated', `Display currency changed to ${newCurrency}.`, 'success');
        } catch (e) {
            console.error(e);
        }
    }

    loadDashboard();
}

/**
 * Filter Parameters
 */
function getFilterParams() {
    const search = document.getElementById('filterSearch').value.trim();
    const type = document.getElementById('filterType').value;
    const category = document.getElementById('filterCategory').value;
    const startDate = document.getElementById('filterStartDate').value;
    const endDate = document.getElementById('filterEndDate').value;

    const params = new URLSearchParams();
    if (search) params.append('search', search);
    if (type) params.append('type', type);
    if (category) params.append('category', category);
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);

    return params;
}

async function loadDashboard() {
    if (!authToken) return;
    await Promise.all([
        fetchSummary(),
        fetchTransactions()
    ]);
}

/**
 * Summary & Charts
 */
async function fetchSummary() {
    try {
        const filterParams = getFilterParams();
        const response = await authFetch(`${API_BASE}/summary?${filterParams.toString()}`);
        if (!response.ok) throw new Error('Failed to load summary');
        const data = await response.json();

        // 1. KPI Cards
        document.getElementById('kpiTotalIncome').textContent = formatCurrency(data.totalIncome || 0);
        document.getElementById('kpiTotalExpense').textContent = formatCurrency(data.totalExpense || 0);

        const balanceEl = document.getElementById('kpiBalance');
        const balance = data.balance || 0;
        balanceEl.textContent = formatCurrency(balance);
        balanceEl.className = `text-2xl font-bold tracking-tight ${balance >= 0 ? 'text-slate-900' : 'text-rose-600'}`;

        document.getElementById('kpiTopCategory').textContent = data.topSpendingCategory || 'None';

        // 2. Budget Health Card
        const budgetLimit = data.monthlyBudgetLimit || 0;
        const usedPercent = data.budgetUsedPercentage || 0;
        const budgetBar = document.getElementById('kpiBudgetBar');

        document.getElementById('kpiBudgetLabel').textContent = `Budget: ${formatCurrency(budgetLimit)}`;
        document.getElementById('kpiBudgetPercent').textContent = `${usedPercent}%`;
        budgetBar.style.width = `${Math.min(usedPercent, 100)}%`;

        if (usedPercent >= 100) {
            budgetBar.className = 'bg-rose-500 h-2 rounded-full transition-all duration-500';
        } else if (usedPercent >= 80) {
            budgetBar.className = 'bg-amber-500 h-2 rounded-full transition-all duration-500';
        } else {
            budgetBar.className = 'bg-emerald-500 h-2 rounded-full transition-all duration-500';
        }

        // 3. Dynamic Alert Banner
        renderBudgetAlert(data);

        // 4. Update Charts
        renderMonthlyTrendChart(data.monthlyTrends || []);
        renderCategoryChart(data.categoryExpenses || {});

    } catch (err) {
        console.error('Error fetching summary:', err);
    }
}

function renderBudgetAlert(summary) {
    const banner = document.getElementById('budgetAlertBanner');
    const icon = document.getElementById('budgetAlertIcon');
    const title = document.getElementById('budgetAlertTitle');
    const text = document.getElementById('budgetAlertText');

    if (summary.alertStatus === 'EXCEEDED') {
        banner.className = 'block rounded-xl p-4 bg-rose-50 border border-rose-200 text-rose-900 animate-fade-in';
        icon.className = 'p-2 rounded-lg bg-rose-100 text-rose-600';
        icon.innerHTML = '<i class="fa-solid fa-triangle-exclamation text-lg"></i>';
        title.textContent = 'Budget Limit Exceeded!';
        text.textContent = summary.alertMessage;
    } else if (summary.alertStatus === 'WARNING') {
        banner.className = 'block rounded-xl p-4 bg-amber-50 border border-amber-200 text-amber-900 animate-fade-in';
        icon.className = 'p-2 rounded-lg bg-amber-100 text-amber-600';
        icon.innerHTML = '<i class="fa-solid fa-bell text-lg"></i>';
        title.textContent = 'Budget Warning (Above 80%)';
        text.textContent = summary.alertMessage;
    } else {
        banner.className = 'hidden';
    }
}

/**
 * Transactions Table & Pagination
 */
async function fetchTransactions() {
    const tableBody = document.getElementById('transactionsTableBody');
    try {
        const params = getFilterParams();
        params.append('page', currentPage);
        params.append('size', pageSize);
        params.append('sortBy', 'transactionDate');
        params.append('direction', 'DESC');

        const response = await authFetch(`${API_BASE}?${params.toString()}`);
        if (!response.ok) throw new Error('Failed to load transactions');
        const data = await response.json();

        totalPages = data.totalPages;

        if (!data.content || data.content.length === 0) {
            tableBody.innerHTML = `
                <tr>
                    <td colspan="7" class="py-12 text-center">
                        <div class="flex flex-col items-center justify-center text-slate-400">
                            <i class="fa-regular fa-folder-open text-4xl mb-3 text-slate-300"></i>
                            <p class="text-sm font-semibold text-slate-600">No transactions found</p>
                            <p class="text-xs text-slate-400 mt-1">Try clearing filters or record a new transaction.</p>
                        </div>
                    </td>
                </tr>`;
        } else {
            tableBody.innerHTML = data.content.map(tx => {
                const isExpense = tx.type === 'EXPENSE';
                const formattedAmount = `${isExpense ? '-' : '+'}${formatCurrency(tx.amount)}`;
                const amountClass = isExpense ? 'text-rose-600' : 'text-emerald-600 font-bold';
                const badgeTypeClass = isExpense ? 'bg-rose-50 text-rose-700 border-rose-200' : 'bg-emerald-50 text-emerald-700 border-emerald-200';

                return `
                    <tr class="hover:bg-slate-50/70 border-b border-slate-100 transition-colors">
                        <td class="py-3.5 pl-6 pr-3 whitespace-nowrap text-slate-500 text-xs font-mono">
                            ${tx.transactionDate}
                        </td>
                        <td class="px-3 py-3.5 text-slate-900 font-semibold max-w-[200px] truncate" title="${escapeHtml(tx.title)}">
                            ${escapeHtml(tx.title)}
                        </td>
                        <td class="px-3 py-3.5 whitespace-nowrap">
                            <span class="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-semibold bg-slate-100 text-slate-700 border border-slate-200">
                                ${escapeHtml(tx.category)}
                            </span>
                        </td>
                        <td class="px-3 py-3.5 whitespace-nowrap">
                            <span class="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border ${badgeTypeClass}">
                                ${tx.type}
                            </span>
                        </td>
                        <td class="px-3 py-3.5 whitespace-nowrap font-mono text-sm ${amountClass}">
                            ${formattedAmount}
                        </td>
                        <td class="px-3 py-3.5 text-slate-500 text-xs max-w-[220px] truncate" title="${escapeHtml(tx.notes || '')}">
                            ${escapeHtml(tx.notes || '-')}
                        </td>
                        <td class="py-3.5 pl-3 pr-6 text-right whitespace-nowrap space-x-2">
                            <button onclick="editTransaction(${tx.id})" title="Edit" class="text-slate-400 hover:text-emerald-600 p-1 transition-colors">
                                <i class="fa-regular fa-pen-to-square"></i>
                            </button>
                            <button onclick="deleteTransaction(${tx.id})" title="Delete" class="text-slate-400 hover:text-rose-600 p-1 transition-colors">
                                <i class="fa-regular fa-trash-can"></i>
                            </button>
                        </td>
                    </tr>
                `;
            }).join('');
        }

        // Render Pagination Info & Direct Page Numbers
        const totalElements = data.totalElements || 0;
        const start = totalElements === 0 ? 0 : currentPage * pageSize + 1;
        const end = Math.min((currentPage + 1) * pageSize, totalElements);
        document.getElementById('paginationInfo').textContent = `Showing ${start} to ${end} of ${totalElements} entries`;

        renderPaginationControls();

    } catch (err) {
        console.error('Error fetching transactions:', err);
        tableBody.innerHTML = `<tr><td colspan="7" class="py-6 text-center text-rose-500 font-semibold text-xs">${err.message}</td></tr>`;
    }
}

function renderPaginationControls() {
    const container = document.getElementById('paginationButtonsContainer');
    if (!container) return;

    if (totalPages <= 1) {
        container.innerHTML = '';
        return;
    }

    let html = `
        <button onclick="goToPage(${currentPage - 1})" ${currentPage === 0 ? 'disabled' : ''}
                class="px-2.5 py-1 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-lg hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed" title="Previous Page">
            <i class="fa-solid fa-chevron-left"></i>
        </button>
    `;

    // Sliding window of max 5 visible pages to prevent overflow
    const delta = 2;
    const startPage = Math.max(0, currentPage - delta);
    const endPage = Math.min(totalPages - 1, currentPage + delta);

    if (startPage > 0) {
        html += `
            <button onclick="goToPage(0)" class="px-3 py-1 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-slate-700 hover:bg-slate-100">1</button>
        `;
        if (startPage > 1) {
            html += `<span class="px-1 text-slate-400 text-xs select-none">...</span>`;
        }
    }

    for (let p = startPage; p <= endPage; p++) {
        const isCurrent = p === currentPage;
        html += `
            <button onclick="goToPage(${p})"
                    class="px-3 py-1 text-xs font-bold rounded-lg ${isCurrent ? 'bg-emerald-600 text-white shadow-xs' : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-100'}">
                ${p + 1}
            </button>
        `;
    }

    if (endPage < totalPages - 1) {
        if (endPage < totalPages - 2) {
            html += `<span class="px-1 text-slate-400 text-xs select-none">...</span>`;
        }
        html += `
            <button onclick="goToPage(${totalPages - 1})" class="px-3 py-1 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-slate-700 hover:bg-slate-100">${totalPages}</button>
        `;
    }

    html += `
        <button onclick="goToPage(${currentPage + 1})" ${currentPage >= totalPages - 1 ? 'disabled' : ''}
                class="px-2.5 py-1 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-lg hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed" title="Next Page">
            <i class="fa-solid fa-chevron-right"></i>
        </button>
    `;

    container.innerHTML = html;
}

function goToPage(page) {
    if (page >= 0 && page < totalPages) {
        currentPage = page;
        fetchTransactions();
    }
}

function resetFilters() {
    document.getElementById('filterSearch').value = '';
    document.getElementById('filterType').value = '';
    document.getElementById('filterCategory').value = '';
    document.getElementById('filterStartDate').value = '';
    document.getElementById('filterEndDate').value = '';
    currentPage = 0;
    loadDashboard();
}

/**
 * Chart.js Visualizations
 */
function renderMonthlyTrendChart(trends) {
    const canvas = document.getElementById('monthlyTrendChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    const labels = trends.map(t => t.month);
    const incomes = trends.map(t => t.income);
    const expenses = trends.map(t => t.expense);

    if (monthlyChart) monthlyChart.destroy();

    monthlyChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'Income',
                    data: incomes,
                    backgroundColor: 'rgba(34, 197, 94, 0.85)',
                    borderRadius: 6
                },
                {
                    label: 'Expense',
                    data: expenses,
                    backgroundColor: 'rgba(244, 63, 94, 0.85)',
                    borderRadius: 6
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: (ctx) => ` ${ctx.dataset.label}: ${formatCurrency(ctx.raw)}`
                    }
                }
            },
            scales: {
                x: { grid: { display: false } },
                y: {
                    beginAtZero: true,
                    ticks: {
                        callback: (value) => formatCurrency(value)
                    },
                    grid: { color: 'rgba(226, 232, 240, 0.6)' }
                }
            }
        }
    });
}

function renderCategoryChart(categoryMap) {
    const canvas = document.getElementById('categoryChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    const labels = Object.keys(categoryMap);
    const data = Object.values(categoryMap);

    if (categoryChart) categoryChart.destroy();

    if (labels.length === 0) {
        categoryChart = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: ['No Expenses'],
                datasets: [{ data: [1], backgroundColor: ['#f1f5f9'], borderWidth: 0 }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '70%',
                plugins: {
                    legend: { display: false },
                    tooltip: { enabled: false }
                }
            },
            plugins: [{
                id: 'emptyDoughnutText',
                afterDraw: (chart) => {
                    const { ctx, width, height } = chart;
                    ctx.save();
                    ctx.font = '600 12px Inter, system-ui, sans-serif';
                    ctx.fillStyle = '#94a3b8';
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'middle';
                    ctx.fillText('No Expenses Yet', width / 2, height / 2);
                    ctx.restore();
                }
            }]
        });
        return;
    }

    const palette = ['#f59e0b', '#6366f1', '#ec4899', '#10b981', '#8b5cf6', '#06b6d4', '#f97316', '#64748b'];

    categoryChart = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: labels,
            datasets: [{
                data: data,
                backgroundColor: palette.slice(0, labels.length),
                borderWidth: 2,
                borderColor: '#ffffff'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '70%',
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: { boxWidth: 10, font: { size: 11 }, padding: 12 }
                },
                tooltip: {
                    callbacks: {
                        label: (ctx) => ` ${ctx.label}: ${formatCurrency(ctx.raw)}`
                    }
                }
            }
        }
    });
}

/**
 * Transaction Modal
 */
function openTransactionModal(editMode = false) {
    document.getElementById('modalError').classList.add('hidden');
    document.getElementById('modalTitle').textContent = editMode ? 'Edit Transaction' : 'Add Transaction';
    const submitBtn = document.getElementById('modalSubmitBtn');
    submitBtn.disabled = false;
    submitBtn.textContent = editMode ? 'Update' : 'Save Transaction';
    cancelQuickCategory();

    if (!editMode) {
        document.getElementById('txForm').reset();
        document.getElementById('txId').value = '';
        initDefaultDates();
    }

    document.getElementById('txModal').classList.remove('hidden');
}

function closeTransactionModal() {
    cancelQuickCategory();
    document.getElementById('txModal').classList.add('hidden');
}

async function editTransaction(id) {
    try {
        const response = await authFetch(`${API_BASE}/${id}`);
        if (!response.ok) throw new Error('Failed to retrieve transaction');
        const tx = await response.json();

        document.getElementById('txId').value = tx.id;
        document.getElementById('txTitle').value = tx.title;
        document.getElementById('txAmount').value = tx.amount;
        document.getElementById('txType').value = tx.type;
        document.getElementById('txCategory').value = tx.category;
        document.getElementById('txDate').value = tx.transactionDate;
        document.getElementById('txNotes').value = tx.notes || '';

        openTransactionModal(true);
    } catch (err) {
        showToast('Error', err.message, 'error');
    }
}

async function handleTransactionSubmit(event) {
    event.preventDefault();
    const id = document.getElementById('txId').value;
    const isEdit = Boolean(id);
    const submitBtn = document.getElementById('modalSubmitBtn');
    const originalText = isEdit ? 'Update' : 'Save Transaction';

    const payload = {
        title: document.getElementById('txTitle').value.trim(),
        amount: parseFloat(document.getElementById('txAmount').value),
        type: document.getElementById('txType').value,
        category: document.getElementById('txCategory').value,
        transactionDate: document.getElementById('txDate').value,
        notes: document.getElementById('txNotes').value.trim()
    };

    const url = isEdit ? `${API_BASE}/${id}` : API_BASE;
    const method = isEdit ? 'PUT' : 'POST';

    // Show "Saving..." loading state and disable button to prevent double-submit
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin mr-1.5"></i> Saving...';

    try {
        const response = await authFetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const errData = await response.json();
            const msg = errData.message || (errData.errors ? Object.values(errData.errors).join(', ') : 'Failed to save');
            throw new Error(msg);
        }

        closeTransactionModal();

        // If new transaction was added, reset pagination to page 0
        if (!isEdit) {
            currentPage = 0;
            // Clear conflicting search or filters so the new entry isn't filtered out
            const activeSearch = document.getElementById('filterSearch').value.trim();
            const activeType = document.getElementById('filterType').value;
            const activeCategory = document.getElementById('filterCategory').value;
            if (activeSearch || 
                (activeType && activeType !== payload.type) || 
                (activeCategory && activeCategory !== payload.category)) {
                document.getElementById('filterSearch').value = '';
                document.getElementById('filterType').value = '';
                document.getElementById('filterCategory').value = '';
                document.getElementById('filterStartDate').value = '';
                document.getElementById('filterEndDate').value = '';
            }
        }

        // Await live dashboard refresh so updated ledger rows, balance, KPIs & charts show up instantly
        await loadDashboard();
        showToast('Success', `Transaction ${isEdit ? 'updated' : 'created'} successfully!`, 'success');

    } catch (err) {
        const errEl = document.getElementById('modalError');
        errEl.textContent = err.message;
        errEl.classList.remove('hidden');
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalText;
    }
}

async function deleteTransaction(id) {
    if (!confirm('Are you sure you want to permanently delete this transaction?')) return;

    // Instant optimistic visual feedback: row immediately dims and disables
    const row = document.querySelector(`button[onclick="deleteTransaction(${id})"]`)?.closest('tr');
    if (row) {
        row.classList.add('opacity-30', 'pointer-events-none', 'bg-rose-50');
    }

    try {
        const response = await authFetch(`${API_BASE}/${id}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('Failed to delete transaction');

        // If last row on page was deleted, step back one page
        const remainingRows = document.querySelectorAll('#transactionsTableBody tr:not(.opacity-30)').length;
        if (remainingRows <= 1 && currentPage > 0) {
            currentPage--;
        }

        // Auto-refresh the dashboard in real time
        await loadDashboard();
        showToast('Deleted', 'Transaction removed.', 'info');
    } catch (err) {
        if (row) {
            row.classList.remove('opacity-30', 'pointer-events-none', 'bg-rose-50');
        }
        showToast('Error', err.message, 'error');
    }
}

/**
 * Budget Modal
 */
async function openBudgetModal() {
    const today = new Date();
    const curMonth = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`;
    const budgetMonthInput = document.getElementById('budgetMonth');
    budgetMonthInput.value = curMonth;

    try {
        const response = await authFetch(`${BUDGET_API}?monthYear=${curMonth}`);
        if (response.ok) {
            const data = await response.json();
            document.getElementById('budgetLimit').value = data.monthlyLimit || '';
        }
    } catch (err) {
        console.error(err);
    }

    document.getElementById('budgetModal').classList.remove('hidden');
}

function closeBudgetModal() {
    document.getElementById('budgetModal').classList.add('hidden');
}

async function handleBudgetMonthChange(monthYear) {
    if (!monthYear || !/^\d{4}-(0[1-9]|1[0-2])$/.test(monthYear)) return;
    try {
        const response = await authFetch(`${BUDGET_API}?monthYear=${monthYear}`);
        if (response.ok) {
            const data = await response.json();
            document.getElementById('budgetLimit').value = (data.monthlyLimit && data.monthlyLimit > 0) ? data.monthlyLimit : '';
        }
    } catch (err) {
        console.error('Failed to fetch budget for selected month:', err);
    }
}

async function handleBudgetSubmit(event) {
    event.preventDefault();
    const submitBtn = event.target.querySelector('button[type="submit"]');
    const originalText = submitBtn ? submitBtn.innerHTML : 'Save Budget';

    let monthVal = document.getElementById('budgetMonth').value.trim();
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(monthVal)) {
        const d = new Date();
        monthVal = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    }

    const limitVal = parseFloat(document.getElementById('budgetLimit').value);
    if (isNaN(limitVal) || limitVal < 0) {
        showToast('Invalid Amount', 'Please enter a valid positive budget limit.', 'error');
        return;
    }

    const payload = {
        monthYear: monthVal,
        monthlyLimit: limitVal
    };

    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin mr-1.5"></i> Saving...';
    }

    try {
        const response = await authFetch(BUDGET_API, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const errData = await response.json().catch(() => ({}));
            const msg = errData.message || (errData.errors ? Object.values(errData.errors).join(', ') : 'Failed to save monthly budget');
            throw new Error(msg);
        }

        closeBudgetModal();
        await loadDashboard();
        showToast('Budget Saved', `Monthly budget updated to ${formatCurrency(payload.monthlyLimit)}.`, 'success');
    } catch (err) {
        showToast('Error', err.message, 'error');
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalText;
        }
    }
}

/**
 * CSV Data Export
 */
async function exportToCSV() {
    const params = getFilterParams();
    try {
        const response = await authFetch(`${API_BASE}/export/csv?${params.toString()}`);
        if (!response.ok) throw new Error('Failed to export CSV');

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `expenses_export_${new Date().toISOString().slice(0, 10)}.csv`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        showToast('Export Completed', 'CSV file downloaded.', 'info');
    } catch (e) {
        showToast('Export Error', e.message, 'error');
    }
}

/**
 * Bank-Grade PDF Statement Export
 */
async function exportToPDF() {
    const params = getFilterParams();
    try {
        showToast('Generating PDF', 'Preparing your statement report...', 'info');
        const response = await authFetch(`${API_BASE}/export/pdf?${params.toString()}`);
        if (!response.ok) throw new Error('Failed to generate PDF statement');

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `expenses_statement_${new Date().toISOString().slice(0, 10)}.pdf`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        showToast('Statement Ready', 'PDF statement downloaded successfully.', 'success');
    } catch (e) {
        showToast('PDF Export Error', e.message, 'error');
    }
}

/**
 * Clear / Bulk Delete Transaction History
 */
function formatIsoDate(d) {
    if (!d || isNaN(d.getTime())) return '';
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
}

function getPresetDateRange(preset) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    switch (preset) {
        case 'today': {
            const str = formatIsoDate(today);
            return { start: str, end: str, label: `${str} (Today)` };
        }
        case 'yesterday': {
            const y = new Date(today);
            y.setDate(today.getDate() - 1);
            const str = formatIsoDate(y);
            return { start: str, end: str, label: `${str} (Yesterday)` };
        }
        case 'this_week': {
            const day = today.getDay();
            const diffToMon = (day === 0 ? -6 : 1) - day;
            const mon = new Date(today);
            mon.setDate(today.getDate() + diffToMon);
            const sun = new Date(mon);
            sun.setDate(mon.getDate() + 6);
            return { start: formatIsoDate(mon), end: formatIsoDate(sun), label: `${formatIsoDate(mon)} to ${formatIsoDate(sun)}` };
        }
        case 'last_week': {
            const day = today.getDay();
            const diffToMon = (day === 0 ? -6 : 1) - day;
            const lastMon = new Date(today);
            lastMon.setDate(today.getDate() + diffToMon - 7);
            const lastSun = new Date(lastMon);
            lastSun.setDate(lastMon.getDate() + 6);
            return { start: formatIsoDate(lastMon), end: formatIsoDate(lastSun), label: `${formatIsoDate(lastMon)} to ${formatIsoDate(lastSun)}` };
        }
        case 'this_month': {
            const first = new Date(today.getFullYear(), today.getMonth(), 1);
            const last = new Date(today.getFullYear(), today.getMonth() + 1, 0);
            return { start: formatIsoDate(first), end: formatIsoDate(last), label: `${formatIsoDate(first)} to ${formatIsoDate(last)}` };
        }
        case 'last_month': {
            const first = new Date(today.getFullYear(), today.getMonth() - 1, 1);
            const last = new Date(today.getFullYear(), today.getMonth(), 0);
            return { start: formatIsoDate(first), end: formatIsoDate(last), label: `${formatIsoDate(first)} to ${formatIsoDate(last)}` };
        }
        case 'last_6_months': {
            const first = new Date(today.getFullYear(), today.getMonth() - 5, 1);
            const last = new Date(today.getFullYear(), today.getMonth() + 1, 0);
            return { start: formatIsoDate(first), end: formatIsoDate(last), label: `${formatIsoDate(first)} to ${formatIsoDate(last)}` };
        }
        case 'this_year': {
            const first = new Date(today.getFullYear(), 0, 1);
            const last = new Date(today.getFullYear(), 11, 31);
            return { start: formatIsoDate(first), end: formatIsoDate(last), label: `${formatIsoDate(first)} to ${formatIsoDate(last)}` };
        }
        case 'all': {
            return { start: null, end: null, label: 'Entire History (All Time)' };
        }
        case 'custom': {
            const s = document.getElementById('historyStartDate').value;
            const e = document.getElementById('historyEndDate').value;
            const lbl = (s && e) ? `${s} to ${e}` : (s ? `From ${s}` : (e ? `Up to ${e}` : 'Select dates'));
            return { start: s || null, end: e || null, label: lbl };
        }
        default:
            return { start: null, end: null, label: 'All Time' };
    }
}

function handleHistoryPresetChange(preset) {
    const customFields = document.getElementById('historyCustomFields');
    const rangeText = document.getElementById('historyRangeText');

    if (preset === 'custom') {
        customFields.classList.remove('hidden');
        if (!document.getElementById('historyStartDate').value) {
            const todayStr = formatIsoDate(new Date());
            document.getElementById('historyStartDate').value = todayStr;
            document.getElementById('historyEndDate').value = todayStr;
        }
    } else {
        customFields.classList.add('hidden');
    }

    const range = getPresetDateRange(preset);
    rangeText.textContent = range.label;
    toggleHistoryDeleteBtn();
}

function updateCustomRangePreview() {
    const range = getPresetDateRange('custom');
    document.getElementById('historyRangeText').textContent = range.label;
    toggleHistoryDeleteBtn();
}

function toggleHistoryDeleteBtn() {
    const check = document.getElementById('historyConfirmCheck');
    const btn = document.getElementById('historyDeleteBtn');
    const preset = document.getElementById('historyRangePreset').value;

    let valid = Boolean(check && check.checked);
    if (preset === 'custom') {
        const s = document.getElementById('historyStartDate').value;
        const e = document.getElementById('historyEndDate').value;
        if (!s || !e) valid = false;
    }

    if (btn) btn.disabled = !valid;
}

function openClearHistoryModal() {
    const modal = document.getElementById('clearHistoryModal');
    if (!modal) return;

    document.getElementById('historyRangePreset').value = 'this_month';
    document.getElementById('historyConfirmCheck').checked = false;
    document.getElementById('historyModalError').classList.add('hidden');
    document.getElementById('historyDeleteBtn').disabled = true;

    const todayStr = formatIsoDate(new Date());
    document.getElementById('historyStartDate').value = todayStr;
    document.getElementById('historyEndDate').value = todayStr;

    handleHistoryPresetChange('this_month');
    modal.classList.remove('hidden');
}

function closeClearHistoryModal() {
    const modal = document.getElementById('clearHistoryModal');
    if (modal) modal.classList.add('hidden');
}

async function confirmDeleteHistory() {
    const preset = document.getElementById('historyRangePreset').value;
    const range = getPresetDateRange(preset);
    const btn = document.getElementById('historyDeleteBtn');
    const errEl = document.getElementById('historyModalError');
    errEl.classList.add('hidden');

    if (preset === 'custom' && range.start && range.end && range.start > range.end) {
        errEl.textContent = 'Start date cannot be after end date.';
        errEl.classList.remove('hidden');
        return;
    }

    btn.disabled = true;
    btn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin mr-1.5"></i> Deleting...';

    const params = new URLSearchParams();
    if (range.start) params.append('startDate', range.start);
    if (range.end) params.append('endDate', range.end);
    const url = `${API_BASE}/history${params.toString() ? '?' + params.toString() : ''}`;

    try {
        const response = await authFetch(url, { method: 'DELETE' });
        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.message || 'Failed to delete transaction history');
        }

        const data = await response.json();
        closeClearHistoryModal();
        currentPage = 0;
        await loadDashboard();
        showToast('History Cleared', data.message || `${data.deletedCount} transaction(s) deleted.`, 'info');
    } catch (err) {
        errEl.textContent = err.message;
        errEl.classList.remove('hidden');
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="fa-regular fa-trash-can mr-1.5"></i> Delete History';
        toggleHistoryDeleteBtn();
    }
}

/**
 * Authentication Handling
 */
function showAuthModal(register = false) {
    isRegisterMode = register;
    document.getElementById('authError').classList.add('hidden');

    const title = document.getElementById('authModalTitle');
    const subtitle = document.getElementById('authModalSubtitle');
    const submitBtn = document.getElementById('authSubmitBtn');
    const toggleBtn = document.getElementById('authToggleBtn');
    const nameField = document.getElementById('authNameField');
    const currencyField = document.getElementById('authCurrencyField');

    if (isRegisterMode) {
        title.textContent = 'Create Account';
        subtitle.textContent = 'Get started with private personal expense tracking';
        submitBtn.textContent = 'Register Account';
        toggleBtn.textContent = 'Already have an account? Sign in';
        nameField.classList.remove('hidden');
        currencyField.classList.remove('hidden');
    } else {
        title.textContent = 'Sign In';
        subtitle.textContent = 'Access your private financial dashboard';
        submitBtn.textContent = 'Sign In';
        toggleBtn.textContent = "Don't have an account? Sign up";
        nameField.classList.add('hidden');
        currencyField.classList.add('hidden');
    }

    document.getElementById('authModal').classList.remove('hidden');
}

function closeAuthModal() {
    document.getElementById('authModal').classList.add('hidden');
}

function toggleAuthMode() {
    showAuthModal(!isRegisterMode);
}

async function handleAuthSubmit(event) {
    event.preventDefault();
    const email = document.getElementById('authEmail').value.trim();
    const password = document.getElementById('authPassword').value;

    const endpoint = isRegisterMode ? `${AUTH_API}/register` : `${AUTH_API}/login`;
    const payload = isRegisterMode ? {
        name: document.getElementById('authName').value.trim(),
        email,
        password,
        currency: document.getElementById('authCurrency').value
    } : { email, password };

    try {
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Authentication failed');
        }

        authToken = data.token;
        currentUser = {
            id: data.userId,
            name: data.name,
            email: data.email,
            currency: data.currency || 'INR'
        };

        localStorage.setItem('expense_token', authToken);
        localStorage.setItem('expense_user', JSON.stringify(currentUser));

        closeAuthModal();
        setLoggedInUI(true);
        loadDashboard();
        showToast('Welcome', `Signed in as ${currentUser.name}!`, 'success');

    } catch (err) {
        const errEl = document.getElementById('authError');
        errEl.textContent = err.message;
        errEl.classList.remove('hidden');
    }
}

function logout(manual = true) {
    authToken = null;
    currentUser = null;
    localStorage.removeItem('expense_token');
    localStorage.removeItem('expense_user');
    setLoggedInUI(false);
    if (manual) showToast('Signed Out', 'You have been logged out.', 'info');
}

/**
 * Toast Notifications
 */
function showToast(title, message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');

    const colorClasses = {
        success: 'bg-emerald-600 text-white',
        error: 'bg-rose-600 text-white',
        info: 'bg-slate-800 text-white'
    }[type] || 'bg-slate-800 text-white';

    toast.className = `${colorClasses} pointer-events-auto px-4 py-3 rounded-xl shadow-lg flex items-center space-x-3 text-xs font-medium animate-fade-in transition-all duration-300`;
    toast.innerHTML = `
        <div class="flex-1">
            <span class="font-bold">${escapeHtml(title)}:</span> ${escapeHtml(message)}
        </div>
        <button onclick="this.parentElement.remove()" class="text-white/80 hover:text-white ml-2">
            <i class="fa-solid fa-xmark"></i>
        </button>
    `;

    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

/**
 * Currency Formatter
 */
function formatCurrency(amount) {
    const config = CURRENCY_CONFIG[userCurrency] || CURRENCY_CONFIG.INR;
    return new Intl.NumberFormat(config.locale, {
        style: 'currency',
        currency: config.currency,
        maximumFractionDigits: 2
    }).format(amount || 0);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
