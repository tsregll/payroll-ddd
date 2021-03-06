package top.dddclub.payroll.payrollcontext.domain.salariedemployee;

import org.hibernate.annotations.DiscriminatorOptions;
import top.dddclub.payroll.core.domain.AbstractEntity;
import top.dddclub.payroll.core.domain.AggregateRoot;
import top.dddclub.payroll.employeecontext.domain.EmployeeId;
import top.dddclub.payroll.payrollcontext.domain.Payrollable;
import top.dddclub.payroll.payrollcontext.domain.Salary;
import top.dddclub.payroll.payrollcontext.domain.Payroll;
import top.dddclub.payroll.payrollcontext.domain.Period;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name="employees")
@DiscriminatorColumn(name = "employeeType", discriminatorType = DiscriminatorType.INTEGER)
@DiscriminatorOptions(force=true)
@DiscriminatorValue(value = "1")
public class SalariedEmployee extends AbstractEntity<EmployeeId> implements AggregateRoot<SalariedEmployee>, Payrollable {
    private static final int WORK_DAYS_OF_MONTH = 22;

    @EmbeddedId
    private EmployeeId employeeId;

    @Embedded
    private Salary salaryOfMonth;

    @ElementCollection
    @CollectionTable(name = "absences", joinColumns = @JoinColumn(name = "employeeId"))
    private List<Absence> absences = new ArrayList<>();

    public SalariedEmployee() {
    }

    public SalariedEmployee(EmployeeId employeeId, Salary salaryOfMonth) {
        this(employeeId, salaryOfMonth, new ArrayList<>());
    }

    public SalariedEmployee(EmployeeId employeeId, Salary salaryOfMonth, List<Absence> absences) {
        this.employeeId = employeeId;
        this.salaryOfMonth = salaryOfMonth;
        this.absences = absences;
    }

    public Salary salaryOfMonth() {
        return this.salaryOfMonth;
    }

    public List<Absence> absences() {
        return this.absences;
    }

    @Override
    public Payroll payroll(Period settlementPeriod) {
        if (Objects.isNull(absences) || absences.isEmpty()) {
            return new Payroll(employeeId, settlementPeriod.beginDate(), settlementPeriod.endDate(), salaryOfMonth);
        }

        Salary salaryOfDay = salaryOfMonth.divide(WORK_DAYS_OF_MONTH);

        Salary deduction = absences.stream()
                .filter(a -> a.isIn(settlementPeriod))
                .filter(a -> !a.isPaidLeave())
                .map(a -> salaryOfDay.multiply(a.deductionRatio()))
                .reduce(Salary.zero(salaryOfDay.currency()), (m, agg) -> agg.add(m));

        return new Payroll(employeeId, settlementPeriod.beginDate(), settlementPeriod.endDate(), salaryOfMonth.subtract(deduction));
    }

    @Override
    public EmployeeId id() {
        return this.employeeId;
    }

    @Override
    public SalariedEmployee root() {
        return this;
    }
}
