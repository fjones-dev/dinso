package se.meepo.dinso.service;

public enum CompanyAction {
  READ(false),
  APPROVE_CASE(true),
  ADD_EMPLOYEE(true),
  CHANGE_SALARY(true),
  REGISTER_LEAVE(true),
  END_EMPLOYMENT(true);

  private final boolean write;

  CompanyAction(boolean write) {
    this.write = write;
  }

  public boolean isWrite() {
    return write;
  }
}
